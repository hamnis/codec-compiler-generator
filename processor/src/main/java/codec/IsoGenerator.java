package codec;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.hamnaberg.codec.annotations.JsonFactory;
import net.hamnaberg.codec.annotations.JsonField;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;

public class IsoGenerator {
    private final Types typeUtils;
    private final Elements elementUtils;

    public IsoGenerator(Types typeUtils, Elements elementUtils) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
    }

    public IsoType generate(TypeElement type) {
        List<? extends Element> fields = getElementsAnnotatedWith(type, JsonField.class);
        Option<ExecutableElement> factory = getElementsAnnotatedWith(type, JsonFactory.class).headOption().flatMap(
                e -> e instanceof ExecutableElement ? Option.some((ExecutableElement)e) : Option.none()
        ).orElse(getConstructor(type, fields.size()));

        if (fields.isEmpty()) {
            throw new ProcessorException(type, "Missing fields");
        }
        if (factory.isEmpty()) {
            throw new ProcessorException(type, "Missing factory");
        }

        return new IsoType(type, fields, factory.get());
    }

    static <A extends Annotation> List<? extends Element> getElementsAnnotatedWith(TypeElement type, Class<A> annotation) {
        List<? extends Element> list = List.ofAll(type.getEnclosedElements());

        return list.filter( e -> e.getAnnotation(annotation) != null);

    }

    static Option<ExecutableElement> getConstructor(TypeElement type, int arity) {
        return Option.ofOptional(ElementFilter.constructorsIn(List.of(type)).stream().filter(e -> e.getParameters().size() == arity).findFirst());
    }
}
