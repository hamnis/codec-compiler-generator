package codec;


import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes("net.hamnaberg.codec.annotations.JsonClass")
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
        IsoGenerator iso = new IsoGenerator(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
        CodecGenerator codecGenerator = new CodecGenerator(processingEnv.getElementUtils());
        try {
            Set<TypeElement> types = ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotations.iterator().next()));
            types.forEach(type -> {
                ClassName name = ClassName.get(type);
                IsoType isoType = iso.generate(type);
                TypeSpec IsoSpec = isoType.toSpec();
                JavaFile isoFile = JavaFile.builder(name.packageName(), IsoSpec).build();
                System.out.println("Iso!");
                System.out.println(isoFile);
                CodecType codecType = codecGenerator.generate(type);
                ClassName generatedCodecs = name.peerClass("GeneratedCodecs");
                FieldSpec fieldSpec = codecType.toSpec(isoType.getIsoType());
                TypeSpec.Builder builder = TypeSpec.classBuilder(generatedCodecs);
                builder.addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
                builder.addField(fieldSpec);
                TypeSpec codecs = builder.build();
                System.out.println("Codecs!");
                JavaFile codecFile = JavaFile.builder(generatedCodecs.packageName(), codecs).build();
                System.out.println(codecFile);

            });
        } catch (ProcessorException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
            return true;
        }

        return true;
    }


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}

