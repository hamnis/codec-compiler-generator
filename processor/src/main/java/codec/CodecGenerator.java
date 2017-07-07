package codec;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import io.vavr.collection.Map;
import net.hamnaberg.codec.annotations.JsonField;
import net.hamnaberg.json.codec.Codecs;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

public class CodecGenerator {

    private Elements elementUtils;

    public CodecGenerator(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    CodecType generate(TypeElement type) {
        Map<String, TypeName> fields = IsoGenerator.getElementsAnnotatedWith(type, JsonField.class).
                toLinkedMap(e -> e.getAnnotation(JsonField.class).value(), e -> ClassName.get(e.asType()));
        return new CodecType(type, fields, getDefaultCodecs());
    }

    private Map<TypeName, String> getDefaultCodecs() {
        HashMap<TypeName, String> defaultCodecs = new HashMap<>();
        TypeElement typeElement = elementUtils.getTypeElement(Codecs.class.getName());
        List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getModifiers().containsAll(EnumSet.of(Modifier.STATIC, Modifier.FINAL)) && enclosedElement.getKind().isField()) {
                DeclaredType enclosingElement = (DeclaredType) enclosedElement.asType();
                TypeMirror first = enclosingElement.getTypeArguments().get(0);
                defaultCodecs.put(TypeName.get(first), String.format("%s.%s", typeElement.getSimpleName(), enclosedElement.getSimpleName()));
            }
        }
        return io.vavr.collection.HashMap.ofAll(defaultCodecs);
    }
}
