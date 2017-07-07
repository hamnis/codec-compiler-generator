package codec;

import com.squareup.javapoet.*;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import net.hamnaberg.json.codec.Iso;

import javax.lang.model.element.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IsoType {
    private final ClassName sourceType;
    private final List<? extends Element> fields;
    private final ExecutableElement factory;

    public IsoType(TypeElement type, List<? extends Element> fields, ExecutableElement factory) {
        this.sourceType = ClassName.get(type);
        this.fields = fields;
        this.factory = factory;
    }


    public TypeSpec toSpec() {
        ClassName tuples = ClassName.get("net.hamnaberg.json.util", "Tuples");
        Tuple2<ParameterizedTypeName, String> tupleType = getTupleType();
        TypeSpec.Builder builder = TypeSpec.enumBuilder(getIsoType());
        builder.addModifiers(Modifier.PUBLIC);
        builder.addEnumConstant("INSTANCE");
        builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Iso.class), sourceType, tupleType._1));
        builder.addMethod(MethodSpec.methodBuilder("get").
                addModifiers(Modifier.PUBLIC).
                addParameter(sourceType, "p").
                returns(tupleType._1).
                addStatement("return $T.of($L)", tuples, fields.map(e2 -> String.format("p.%s", e2.getSimpleName())).mkString(", ")).
                build());

        builder.addMethod(MethodSpec.methodBuilder("reverseGet").
                addModifiers(Modifier.PUBLIC).
                addParameter(tupleType._1, "t").
                returns(sourceType).
                addStatement("return $L($L)", factoryString(sourceType), tupleType._2).
                build());

        return builder.build();
    }

    public ClassName getIsoType() {
        return sourceType.peerClass(sourceType.simpleName() + "Iso");
    }

    private String factoryString(ClassName sourceType) {
        if (factory.getKind() == ElementKind.CONSTRUCTOR) {
            return String.format("new %s", sourceType.simpleName());
        } else {
            if (factory.getModifiers().contains(Modifier.STATIC)) {
                return sourceType.simpleName() + "." + factory.getSimpleName();
            }
        }
        throw new RuntimeException("Wtf!");
    }

    private Tuple2<ParameterizedTypeName, String> getTupleType() {
        int arity = fields.size();
        String packageName = arity < 9 ? "io.vavr" : "net.hamnaberg.json.util";
        Seq<TypeName> types = fields.map(e -> TypeName.get(e.asType()).box());

        ParameterizedTypeName tuple = ParameterizedTypeName.get(
                ClassName.get(packageName, String.format("Tuple%s", arity)), types.toJavaArray(TypeName.class));
        String tupleValues = IntStream.range(1, arity + 1).mapToObj(i -> String.format("t._%s", i)).collect(Collectors.joining(", "));
        return Tuple.of(tuple, tupleValues);
    }
}
