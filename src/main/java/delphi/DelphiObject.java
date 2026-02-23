package delphi;

import java.util.*;

/**
 * Represents an instance of a Delphi class at runtime.
 */
public class DelphiObject {

    /** The class definition this object belongs to */
    private final ClassDefinition classDef;

    /** Instance field values */
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public DelphiObject(ClassDefinition classDef) {
        this.classDef = classDef;
        // Initialize all fields to default values
        for (String field : classDef.getAllFieldNames()) {
            fields.put(field.toLowerCase(), null);
        }
    }

    public ClassDefinition getClassDef() {
        return classDef;
    }

    public Object getField(String name) {
        String key = name.toLowerCase();
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        throw new RuntimeException("Field '" + name + "' not found on object of class '" + classDef.getName() + "'");
    }

    public void setField(String name, Object value) {
        String key = name.toLowerCase();
        if (fields.containsKey(key)) {
            fields.put(key, value);
        } else {
            throw new RuntimeException("Field '" + name + "' not found on object of class '" + classDef.getName() + "'");
        }
    }

    public boolean hasField(String name) {
        return fields.containsKey(name.toLowerCase());
    }

    @Override
    public String toString() {
        return "[" + classDef.getName() + " object]";
    }
}
