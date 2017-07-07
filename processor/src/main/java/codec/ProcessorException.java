package codec;

import javax.lang.model.element.Element;

public class ProcessorException extends RuntimeException {
    private Element element;

    ProcessorException(Element element, String msg, Object... args) {
        super(String.format(msg, args));
        this.element = element;
    }

    Element getElement() {
        return element;
    }
}
