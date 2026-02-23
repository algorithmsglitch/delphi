package delphi;

import java.util.*;

/**
 * Represents a variable scope (environment) in the interpreter.
 * Supports lexical scoping via a parent chain.
 */
public class Environment {

    private final Map<String, Object> variables = new HashMap<>();
    private final Environment parent;

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public Environment() {
        this(null);
    }

    /** Define a new variable in THIS scope */
    public void define(String name, Object value) {
        variables.put(name.toLowerCase(), value);
    }

    /** Get a variable, searching parent scopes */
    public Object get(String name) {
        String key = name.toLowerCase();
        if (variables.containsKey(key)) return variables.get(key);
        if (parent != null) return parent.get(key);
        throw new RuntimeException("Undefined variable: " + name);
    }

    /** Assign to an existing variable anywhere in the scope chain */
    public void assign(String name, Object value) {
        String key = name.toLowerCase();
        if (variables.containsKey(key)) {
            variables.put(key, value);
            return;
        }
        if (parent != null) {
            parent.assign(key, value);
            return;
        }
        // If not found, define it globally
        variables.put(key, value);
    }

    public boolean has(String name) {
        String key = name.toLowerCase();
        if (variables.containsKey(key)) return true;
        if (parent != null) return parent.has(key);
        return false;
    }

    public Environment getParent() { return parent; }
}
