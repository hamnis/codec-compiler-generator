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
            Option<String> maybeCodec = defaultCodecs.get(tuple._2.box());
            String codecName = maybeCodec.getOrElse(() -> ((ClassName) tuple._2.box()).simpleName() + "Codec");
            return CodeBlock.of("$L.field($S)", codecName, tuple._1);
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
}
