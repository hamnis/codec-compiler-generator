package codec;

import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

public class TypedName {
    private final TypeName type;
    private final String name;

    public TypedName(TypeName type, String name) {
        this.type = type;
        this.name = name;
    }

    public static TypedName from(Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            return new TypedName(TypeName.get(element.asType()), element.getSimpleName().toString() + "()");
        }
        return new TypedName(TypeName.get(element.asType()), element.getSimpleName().toString());
    }

    public TypeName getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedName typedName = (TypedName) o;

        if (!type.equals(typedName.type)) return false;
        return name.equals(typedName.name);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TypedName{" +
                "type=" + type +
                ", name='" + name + '\'' +
                '}';
    }
}
