package codec;

import com.squareup.javapoet.*;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import net.hamnaberg.json.codec.Codecs;
import net.hamnaberg.json.codec.JsonCodec;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class CodecType {
    private final ClassName poetType;
    private final Map<String, TypeName> fields;
    private final Map<TypeName, String> defaultCodecs;

    public CodecType(TypeElement type, Map<String, TypeName> fields, Map<TypeName, String> defaultCodecs) {
        this.poetType = ClassName.get(type);
        this.fields = fields;
        this.defaultCodecs = defaultCodecs;
    }

    public FieldSpec toSpec(ClassName isoType) {
        ParameterizedTypeName name = ParameterizedTypeName.get(ClassName.get(JsonCodec.class), poetType);
        FieldSpec.Builder builder = FieldSpec.builder(name, poetType.simpleName() + "Codec", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        Seq<CodeBlock> blocks = fields.map((tuple) -> {
            TypeName type = tuple._2.box();

            if (type instanceof ParameterizedTypeName) {
                return parameterized((ParameterizedTypeName) type, tuple._1);
            } else if (type instanceof ClassName) {
                return getCodeBlock((ClassName) type, tuple._1);
            } else {
                throw new RuntimeException();
            }
        });
        String statement = blocks.mkString(", ");
        builder.initializer(
                "$T.codec($T.INSTANCE, $L)",
                ClassName.get(Codecs.class),
                isoType,
                statement
        );
        return builder.build();
    }

    private CodeBlock getCodeBlock(ClassName type, String field) {
        CodeBlock codecName = getCodecName(type);
        return CodeBlock.of("$L.field($S)", codecName, field);
    }

    private CodeBlock getCodecName(ClassName type) {
        Option<CodeBlock> maybeCodec = defaultCodecs.get(type).map(CodeBlock::of);

        return maybeCodec.getOrElse(() -> {
            if (type.packageName().equals(poetType.packageName())) {
                return CodeBlock.of(type.simpleName() + "Codec");
            }
            ClassName target = ClassName.get(type.packageName(), "GeneratedCodecs");
            return CodeBlock.of("$T.$L", target, (type.simpleName() + "Codec"));
        });
    }

    private CodeBlock parameterized(ParameterizedTypeName type, String field) {
        TypeName first = type.typeArguments.get(0);
        CodeBlock inner = getCodecName((ClassName) first);

        String outer;
        switch (type.rawType.toString()) {
            case "io.vavr.collection.List": {
                outer = "Codecs.listCodec($L)";
                break;
            }
            case "java.util.List": {
                outer = "Codecs.javaListCodec($L)";
                break;
            }
            case "io.vavr.control.Option": {
                outer = "Codecs.OptionCodec($L)";
                break;
            }
            case "java.util.Optional": {
                outer = "Codecs.OptionalCodec($L)";
                break;
            }
            default:
                throw new RuntimeException("Unknown parameterized type: " + type.rawType);
        }

        return CodeBlock.of(outer + ".field($S)", inner, field);
    }
}
