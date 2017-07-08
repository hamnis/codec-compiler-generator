package codec;


import com.squareup.javapoet.*;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.hamnaberg.json.annotations.JsonClass;
import net.hamnaberg.json.annotations.JsonField;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

@SupportedAnnotationTypes({"JsonClass", "JsonFactory", "JsonField"})
public class JsonCodecProcessor extends AbstractProcessor {
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
        IsoGenerator iso = new IsoGenerator();
        CodecGenerator codecGenerator = new CodecGenerator(processingEnv.getElementUtils());

        try {
            Set<TypeElement> types = ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(JsonClass.class));
            List<Tuple2<String, TypeElement>> list = types.stream().map(a -> Tuple.of(ClassName.get(a).packageName(), a)).collect(List.collector());
            Map<String, List<TypeElement>> groupedByPackage = list.groupBy(Tuple2::_1).mapValues(list2 -> sortTypes(list2.map(Tuple2::_2)));
            ArrayList<JavaFile> files = new ArrayList<>();

            groupedByPackage.forEach((pkg, typeList) -> {
                TypeSpec.Builder builder = TypeSpec.classBuilder(ClassName.get(pkg, "GeneratedCodecs"));
                builder.addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
                typeList.forEach(type -> {
                    IsoType isoType = iso.generate(type);
                    TypeSpec IsoSpec = isoType.toSpec();
                    files.add(JavaFile.builder(pkg, IsoSpec).build());

                    CodecType codecType = codecGenerator.generate(type);
                    FieldSpec fieldSpec = codecType.toSpec(isoType.getIsoType());
                    builder.addField(fieldSpec);
                });
                files.add(JavaFile.builder(pkg, builder.build()).build());
            });

            for (JavaFile file : files) {
                System.out.println(file);
                file.writeTo(filer);
            }
        } catch (ProcessorException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
        }
        catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }

        return true;
    }

    List<TypeElement> sortTypes(List<TypeElement> elements) {
        List<TypeName> types = supportedTypesWithParameterized(elements.map(ClassName::get));

        return elements.map(type -> {
            List<TypeName> fieldTypes = IsoGenerator.getElementsAnnotatedWith(type, JsonField.class).map(e -> TypeName.get(e.asType()));
            return Tuple.of(type, fieldTypes.filter(types::contains).size());
        }).sorted(Comparator.comparing(Tuple2::_2)).map(Tuple2::_1);
    }

    List<TypeName> supportedTypesWithParameterized(List<TypeName> types) {
        List<ClassName> supportedParameterizedTypes = List.of(
                ClassName.bestGuess("io.vavr.control.Option"),
                ClassName.bestGuess("io.vavr.collection.List"),
                ClassName.bestGuess("java.util.Optional"),
                ClassName.bestGuess("java.util.List")
        );

        return types.flatMap(type -> supportedParameterizedTypes.map(cn -> ParameterizedTypeName.get(cn, type).box()).prepend(type));
    }


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}

