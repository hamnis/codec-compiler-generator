package codec;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.hamnaberg.json.annotations.JsonField;

import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;

public final class Fields {
    private final Map<String, TypedName> fields;

    public Fields(Map<String, TypedName> fields) {
        this.fields = fields;
    }

    public Map<String, TypedName> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return fields.toString();
    }

    public static Fields fieldsFrom(TypeElement type) {
        List<? extends Element> jsonFields = IsoGenerator.getElementsAnnotatedWith(type, JsonField.class);
        if (jsonFields.isEmpty()) {
            jsonFields = List.ofAll(ElementFilter.fieldsIn(type.getEnclosedElements())).filter(Fields::isValidField);
            if (jsonFields.isEmpty()) {
                jsonFields = List.ofAll(ElementFilter.methodsIn(type.getEnclosedElements())).filter(Fields::isValidMethod);
            }
        }

        LinkedHashMap<String, TypedName> fields = new LinkedHashMap<>();

        for (Element field : jsonFields) {
            String fieldName = field.getSimpleName().toString();

            JsonField annotation = field.getAnnotation(JsonField.class);
            if (annotation != null) {
                String annValue = annotation.value();
                if (!annValue.trim().isEmpty()) {
                    fieldName = annValue;
                }
            }
            if (field.getKind() == ElementKind.METHOD) {
                fieldName = fieldName.replaceFirst("get|is", "");
            }
            fields.put(fieldName, TypedName.from(field));
        }
        return new Fields(io.vavr.collection.LinkedHashMap.ofAll(fields));
    }

    private static boolean isValidMethod(ExecutableElement method) {
        if (method.getModifiers().contains(Modifier.PUBLIC)) {
            String methodName = method.getSimpleName().toString();
            return methodName.startsWith("get") || methodName.startsWith("is");
        }
        return false;
    }

    private static boolean isValidField(VariableElement variableElement) {
        return variableElement.getModifiers().containsAll(EnumSet.of(Modifier.FINAL, Modifier.PUBLIC));
    }

    public int size() {
        return fields.size();
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
