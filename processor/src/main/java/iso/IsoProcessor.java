package iso;


import com.squareup.javapoet.*;
import net.hamnaberg.json.Iso;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SupportedAnnotationTypes("iso.IsoTarget")
public class IsoProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<TypeElement> classes = roundEnv.getElementsAnnotatedWith(IsoTarget.class).
                stream().filter(e -> e.getKind() == ElementKind.CLASS).map(e -> (TypeElement) e).collect(Collectors.toList());

        try {
            if (!classes.isEmpty()) {
                classes.forEach(e -> {
                    try {
                        validate(e);
                        createIso(e);
                    } catch (ProcessorException e1) {
                        messager.printMessage(Diagnostic.Kind.ERROR, e1.getMessage(), e);
                    }
                });
            }

        } catch (ProcessorException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
        }


        return true;
    }

    private void createIso(TypeElement e) {
        Stream<ExecutableElement> constructors = ElementFilter.constructorsIn(e.getEnclosedElements()).stream();

        List<? extends Element> methodsOrFields = methodsOrFields(e);
        if (methodsOrFields.isEmpty()) {
            throw new ProcessorException(e, "There where no fields or getters");
        }
        else if (methodsOrFields.size() > 8 ) {
            throw new ProcessorException(e, "We only support max 8 fields");
        }

        Optional<ExecutableElement> maybeCtor = constructors.
                filter(c -> c.getParameters().size() == methodsOrFields.size()).findFirst();

        if (!maybeCtor.isPresent()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "No usable constructor matching arity of fields or getters found", e);
        }
        else {
            ExecutableElement ctor = maybeCtor.get();
            List<? extends VariableElement> parameters = ctor.getParameters();
            if (!validateCtor(methodsOrFields, ctor, parameters)) {
                throw new ProcessorException(e, "We did not find a matching constructor");
            }

            TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(e.getSimpleName() + "Iso");

            TupleHandler tupleHandler = new TupleHandler(parameters).invoke();
            ParameterizedTypeName tuple = tupleHandler.getTuple();
            String tupleValues = tupleHandler.getTupleValues();

            ClassName targetName = ClassName.get(e);

            String targetValues = methodsOrFields.stream().map(e2 -> String.format("p.%s", e2.getSimpleName())).
                    collect(Collectors.joining(", "));


            TypeSpec isoType = enumBuilder.
                    addSuperinterface(ParameterizedTypeName.get(ClassName.get(Iso.class), targetName, tuple)).
                    addModifiers(Modifier.PUBLIC).
                    addEnumConstant("INSTANCE").
                    addMethod(MethodSpec.methodBuilder("get").
                            addModifiers(Modifier.PUBLIC).
                            addParameter(targetName, "p").
                            returns(tuple).
                            addStatement("return new $T<>($L)", tuple.rawType, targetValues).
                            build()).
                    addMethod(MethodSpec.methodBuilder("reverseGet").
                            addModifiers(Modifier.PUBLIC).
                            addParameter(tuple, "t").
                            returns(targetName).
                            addStatement("return new $T($L)", targetName, tupleValues).
                            build()).
                    build();

            PackageElement packageOf = processingEnv.getElementUtils().getPackageOf(e.getEnclosingElement());
            JavaFile.Builder fb = JavaFile.builder(packageOf.getQualifiedName().toString(), isoType);
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
        return method.getModifiers().contains(Modifier.PUBLIC) &&
                method.getSimpleName().toString().startsWith("get");
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

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private class TupleHandler {
        private List<? extends VariableElement> parameters;
        private ParameterizedTypeName tuple;
        private String tupleValues;

        TupleHandler(List<? extends VariableElement> parameters) {
            this.parameters = parameters;
        }

        ParameterizedTypeName getTuple() {
            return tuple;
        }

        String getTupleValues() {
            return tupleValues;
        }

        TupleHandler invoke() {
            List<TypeName> list = parameters.stream().map(e1 -> getType(e1).box()).collect(Collectors.toList());
            int arity = parameters.size();
            tuple = ParameterizedTypeName.get(ClassName.get("javaslang", String.format("Tuple%s", arity)), list.toArray(new TypeName[list.size()]));
            tupleValues = IntStream.range(1, arity + 1).mapToObj(i -> String.format("t._%s", i)).collect(Collectors.joining(", "));
            return this;
        }
    }
}

class ProcessorException extends RuntimeException {
    private Element element;

    ProcessorException(Element element, String msg, Object... args) {
        super(String.format(msg, args));
        this.element = element;
    }

    public Element getElement() {
        return element;
    }
}
