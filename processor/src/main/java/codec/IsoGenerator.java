package codec;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.hamnaberg.json.annotations.JsonFactory;
import net.hamnaberg.json.annotations.JsonField;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;

public class IsoGenerator {

    public IsoType generate(TypeElement type) {
        Fields fields = Fields.fieldsFrom(type);
        Option<ExecutableElement> factory = getElementsAnnotatedWith(type, JsonFactory.class).headOption().flatMap(
                e -> e instanceof ExecutableElement ? Option.some((ExecutableElement)e) : Option.none()
        ).orElse(() -> getConstructor(type, fields.size()));

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

    private static Option<ExecutableElement> getConstructor(TypeElement type, int arity) {
        return Option.ofOptional(
                ElementFilter.constructorsIn(type.getEnclosedElements())
                        .stream().filter(e -> e.getParameters().size() == arity).findFirst()
        );
    }
}
