package iso;


import com.squareup.javapoet.*;
import javaslang.Tuple;
import javaslang.Tuple2;
import net.hamnaberg.json.Codecs;
import net.hamnaberg.json.Iso;
import net.hamnaberg.json.JsonCodec;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SupportedAnnotationTypes("iso.GeneratedCodec")
public class GeneratedCodecProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;
    private ClassName jsonCodecName = ClassName.get(JsonCodec.class);
    private ClassName CodecsName = ClassName.get(Codecs.class);

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<TypeElement> classes = roundEnv.getElementsAnnotatedWith(GeneratedCodec.class).
                stream().filter(e -> e.getKind() == ElementKind.CLASS).map(e -> (TypeElement) e).collect(Collectors.toList());

        try {
            if (!classes.isEmpty()) {
                Map<TypeName, String> codecs = getDefaultCodecs();

                classes.forEach(type -> {
                    ClassName generatedCodecsName = getGeneratedName(type);
                    TypeName typeName = getType(type);
                    codecs.put(typeName, String.format("%s.%s", generatedCodecsName, String.format("%sCodec", type.getSimpleName())));
                });

                CodecGenerator generator = new CodecGenerator();
                classes.forEach(e -> {
                    validate(e);
                    String targetPackage = getPackage(e.getAnnotation(GeneratedCodec.class), e);
                    IsoContainer iso = createIso(e, targetPackage);
                    generator.add(iso, e, codecs);
                });

                generator.writeTo(filer);
            }

        } catch (ProcessorException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }


        return true;
    }

    private ClassName getGeneratedName(TypeElement type) {
        String targetPackage = getPackage(type.getAnnotation(GeneratedCodec.class), type);
        return ClassName.get(targetPackage, "GeneratedCodecs");
    }

    private String getPackage(GeneratedCodec generatedCodec, TypeElement e) {
        String target = generatedCodec.targetPackage();
        TypeName type = getType(e);
        if (target.trim().isEmpty()) {
            target = ((ClassName)type).packageName();
        }
        return target;
    }

    private String useCodecs(Map<TypeName, String> codecs, Map<String, TypeName> fields) {
        List<String> list = new ArrayList<>();
        for (TypeName tn : fields.values()) {
            String codec = codecs.get(tn.box());
            list.add(codec);
        }

        return list.stream().collect(Collectors.joining(", "));
    }

    private IsoContainer createIso(TypeElement e, String target) {

        Stream<ExecutableElement> constructors = ElementFilter.constructorsIn(e.getEnclosedElements()).stream();

        List<? extends Element> methodsOrFields = methodsOrFields(e);
        if (methodsOrFields.isEmpty()) {
            throw new ProcessorException(e, "There where no fields or getters");
        }
        else if (methodsOrFields.size() > 8 ) {
            throw new ProcessorException(e, "We only support max 8 fields");
        }
        PackageElement packageOf = processingEnv.getElementUtils().getPackageOf(e.getEnclosingElement());

        Optional<ExecutableElement> maybeCtor = constructors.
                filter(c -> c.getParameters().size() == methodsOrFields.size()).findFirst();

        if (!maybeCtor.isPresent()) {
            throw new ProcessorException(e, "No usable constructor matching arity of fields or getters found");
        }
        else {
            ExecutableElement ctor = maybeCtor.get();
            List<? extends VariableElement> parameters = ctor.getParameters();
            if (!validateCtor(methodsOrFields, ctor, parameters)) {
                throw new ProcessorException(e, "We did not find a matching constructor");
            }

            TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(e.getSimpleName() + "Iso");

            Tuple2<ParameterizedTypeName, String> tupleHandler = getTuple(parameters);
            ParameterizedTypeName tupleType = tupleHandler._1;

            ClassName targetName = ClassName.get(e);

            String targetValues = methodsOrFields.stream().map(e2 -> String.format("p.%s", e2.getSimpleName())).
                    collect(Collectors.joining(", "));


            TypeSpec isoType = enumBuilder.
                    addSuperinterface(ParameterizedTypeName.get(ClassName.get(Iso.class), targetName, tupleType)).
                    addModifiers(Modifier.PUBLIC).
                    addEnumConstant("INSTANCE").
                    addMethod(MethodSpec.methodBuilder("get").
                            addModifiers(Modifier.PUBLIC).
                            addParameter(targetName, "p").
                            returns(tupleType).
                            addStatement("return new $T<>($L)", tupleType.rawType, targetValues).
                            build()).
                    addMethod(MethodSpec.methodBuilder("reverseGet").
                            addModifiers(Modifier.PUBLIC).
                            addParameter(tupleType, "t").
                            returns(targetName).
                            addStatement("return new $T($L)", targetName, tupleHandler._2).
                            build()).
                    build();

            JavaFile.Builder fb = JavaFile.builder(target, isoType);
            try {
                fb.build().writeTo(filer);
            } catch (IOException e1) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format("Failed to generate IsoFile\n message '%s'", e1.getMessage()),
                        e
                );
            }
        }
        Map<String, TypeName> fields = methodsOrFields.stream().map(elem -> Tuple.of(fieldNameOf(elem), getType(elem))).collect(Collectors.toMap(tup -> tup._1, tup -> tup._2));
        return new IsoContainer(fields, ClassName.get(packageOf.getQualifiedName().toString(), e.getSimpleName() + "Iso"));
    }

    private String fieldNameOf(Element elem) {
        String basename = elem.getSimpleName().toString();
        if (elem.getKind() == ElementKind.METHOD) {
            if (basename.startsWith("get")) return basename.substring("get".length());
            else if (basename.startsWith("is")) return basename.substring("is".length());
            return basename;
        }
        return basename;
    }

    private Map<TypeName, String> getDefaultCodecs() {
        Map<TypeName, String> defaultCodecs = new HashMap<>();
        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(Codecs.class.getName());
        List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getModifiers().containsAll(EnumSet.of(Modifier.STATIC, Modifier.FINAL)) && enclosedElement.getKind().isField()) {
                DeclaredType enclosingElement = (DeclaredType) enclosedElement.asType();
                TypeMirror first = enclosingElement.getTypeArguments().get(0);
                defaultCodecs.put(TypeName.get(first), String.format("%s.%s", typeElement.getSimpleName(), enclosedElement.getSimpleName()));
            }
        }
        return defaultCodecs;
    }

    private boolean validateCtor(List<? extends Element> methodsOrFields, ExecutableElement ctor, List<? extends VariableElement> parameters) {
        Iterator<? extends Element> fIterator = methodsOrFields.iterator();
        Iterator<? extends VariableElement> pIterator = parameters.iterator();
        while(fIterator.hasNext() && pIterator.hasNext()) {
            VariableElement param = pIterator.next();
            Element field = fIterator.next();
            TypeName fType = getType(field);
            TypeName pType = getType(param);
            if (!pType.equals(fType)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "constructor did not match types for fields in order", ctor);
                return false;
            }
        }
        return true;
    }

    private TypeName getType(Element field) {
        TypeMirror typeMirror = field.asType();
        return ClassName.get(typeMirror);
    }

    private List<? extends Element> methodsOrFields(Element e) {
        List<VariableElement> f = ElementFilter.fieldsIn(e.getEnclosedElements());
        List<VariableElement> fields = f
                        .stream()
                        .filter(this::isValidField)
                        .collect(Collectors.toList());

        if ( fields.isEmpty() ) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Did not find any public final fields.", e);
        }

        List<ExecutableElement> getters =
                ElementFilter.methodsIn(e.getEnclosedElements())
                        .stream()
                        .filter(this::isValidGetter)
                        .collect(Collectors.toList());

        if (fields.isEmpty() && getters.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Did not find any getters or public final fields.", e);
        }
        return fields.isEmpty() ? getters : fields;
    }

    private boolean isValidGetter(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        return method.getModifiers().contains(Modifier.PUBLIC) &&
                (methodName.startsWith("get") || methodName.startsWith("is"));
    }

    private boolean isValidField(VariableElement field) {
        return field.getModifiers().containsAll(Arrays.asList(Modifier.PUBLIC, Modifier.FINAL));
    }

    private void validate(TypeElement e) throws ProcessorException {
        if (!e.getModifiers().contains(Modifier.PUBLIC)) {
            throw new ProcessorException(e, "The class %s is not public.", e.getQualifiedName().toString());
        }
        if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessorException(e, "The class %s is abstract, Cannot instantate.", e.getQualifiedName().toString());
        }
    }


    private Tuple2<ParameterizedTypeName, String> getTuple(List<? extends VariableElement> parameters) {
        List<TypeName> list = parameters.stream().map(e1 -> getType(e1).box()).collect(Collectors.toList());
        int arity = parameters.size();
        ParameterizedTypeName tuple = ParameterizedTypeName.get(ClassName.get("javaslang", String.format("Tuple%s", arity)), list.toArray(new TypeName[list.size()]));
        String tupleValues = IntStream.range(1, arity + 1).mapToObj(i -> String.format("t._%s", i)).collect(Collectors.joining(", "));
        return Tuple.of(tuple, tupleValues);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private class IsoContainer {
        Map<String, TypeName> fields;
        ClassName generatedType;

        public IsoContainer(Map<String, TypeName> fields, ClassName generatedType) {
            this.fields = fields;
            this.generatedType = generatedType;
        }
    }

    private class CodecGenerator {
        private Map<String, TypeSpec.Builder> builders = new HashMap<>();

        void add(IsoContainer iso, TypeElement e, Map<TypeName, String> codecs) {
            ClassName generatedCodecsName = getGeneratedName(e);

            TypeSpec.Builder builder = builders.get(generatedCodecsName.packageName());

            if (builder == null) {
                builder = TypeSpec.classBuilder(generatedCodecsName);
                builder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
                builder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
                builders.put(generatedCodecsName.packageName(), builder);
            }
            TypeName typeName = getType(e);
            ParameterizedTypeName codecType = ParameterizedTypeName.get(jsonCodecName, typeName);
            String fieldNames = iso.fields.keySet().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
            String typedCodecs = useCodecs(codecs, iso.fields);
            FieldSpec.Builder b = FieldSpec.builder(codecType, String.format("%sCodec", e.getSimpleName()), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
            b.initializer(
                    "$T.codec$L($T.INSTANCE, $L).apply($L)",
                    CodecsName,
                    iso.fields.size(),
                    iso.generatedType,
                    typedCodecs,
                    fieldNames
            );
            builder.addField(b.build());
        }

        void writeTo(Filer filer) throws IOException {
            for (Map.Entry<String, TypeSpec.Builder> builder : builders.entrySet()) {
                TypeSpec spec = builder.getValue().build();
                JavaFile.Builder fb = JavaFile.builder(builder.getKey(), spec);
                fb.build().writeTo(filer);
            }
        }
    }
}

class ProcessorException extends RuntimeException {
    private Element element;

    ProcessorException(Element element, String msg, Object... args) {
        super(String.format(msg, args));
        this.element = element;
    }

    Element getElement() {
        return element;
    }
}
