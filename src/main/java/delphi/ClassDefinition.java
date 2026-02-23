package delphi;

import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;

/**
 * Stores the definition of a Delphi class, including its fields,
 * methods, constructor, destructor, parent class, and visibility.
 */
public class ClassDefinition {

    public enum Visibility { PRIVATE, PROTECTED, PUBLIC, PUBLISHED }

    public static class FieldInfo {
        public String name;
        public String typeName;
        public Visibility visibility;
        public FieldInfo(String name, String typeName, Visibility visibility) {
            this.name = name;
            this.typeName = typeName;
            this.visibility = visibility;
        }
    }

    public static class MethodInfo {
        public String name;
        public List<ParameterInfo> params;
        public String returnType;   // null for procedures
        public Visibility visibility;
        public ParseTree body;      // the block parse tree
        public boolean isVirtual;
        public boolean isOverride;
        public boolean isAbstract;
        public String ownerClass;   // class that implements this method
        public MethodInfo(String name, List<ParameterInfo> params, String returnType,
                          Visibility visibility, ParseTree body, String ownerClass) {
            this.name = name;
            this.params = params;
            this.returnType = returnType;
            this.visibility = visibility;
            this.body = body;
            this.ownerClass = ownerClass;
        }
    }

    public static class ParameterInfo {
        public String name;
        public String typeName;
        public boolean isVar;
        public ParameterInfo(String name, String typeName, boolean isVar) {
            this.name = name;
            this.typeName = typeName;
            this.isVar = isVar;
        }
    }

    private final String name;
    private ClassDefinition parentClass; // for inheritance
    private final List<String> implementedInterfaces = new ArrayList<>();

    // Fields (insertion ordered, key = lowercase name)
    private final Map<String, FieldInfo> fields = new LinkedHashMap<>();

    // Methods (key = lowercase name)
    private final Map<String, MethodInfo> methods = new LinkedHashMap<>();

    // Constructor & Destructor
    private MethodInfo constructor;
    private MethodInfo destructor;

    public ClassDefinition(String name) {
        this.name = name;
    }

    // ----------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------

    public String getName() { return name; }

    public void setParentClass(ClassDefinition parent) { this.parentClass = parent; }
    public ClassDefinition getParentClass() { return parentClass; }

    public void addInterface(String iface) { implementedInterfaces.add(iface); }

    // ----------------------------------------------------------------
    // Field management
    // ----------------------------------------------------------------

    public void addField(FieldInfo fi) {
        fields.put(fi.name.toLowerCase(), fi);
    }

    public FieldInfo getField(String name) {
        String key = name.toLowerCase();
        if (fields.containsKey(key)) return fields.get(key);
        if (parentClass != null) return parentClass.getField(name);
        return null;
    }

    public boolean hasField(String name) {
        return getField(name) != null;
    }

    /** Returns all field names (including inherited) for initializing objects */
    public List<String> getAllFieldNames() {
        List<String> result = new ArrayList<>();
        if (parentClass != null) result.addAll(parentClass.getAllFieldNames());
        for (String k : fields.keySet()) {
            if (!result.contains(k)) result.add(k);
        }
        return result;
    }

    // ----------------------------------------------------------------
    // Method management
    // ----------------------------------------------------------------

    public void addMethod(MethodInfo mi) {
        methods.put(mi.name.toLowerCase(), mi);
    }

    /** Look up a method, walking up the inheritance chain. */
    public MethodInfo getMethod(String name) {
        String key = name.toLowerCase();
        if (methods.containsKey(key)) return methods.get(key);
        if (parentClass != null) return parentClass.getMethod(name);
        return null;
    }

    public boolean hasOwnMethod(String name) {
        return methods.containsKey(name.toLowerCase());
    }

    // ----------------------------------------------------------------
    // Constructor / Destructor
    // ----------------------------------------------------------------

    public void setConstructor(MethodInfo ctor) { this.constructor = ctor; }
    public MethodInfo getConstructor() {
        if (constructor != null) return constructor;
        if (parentClass != null) return parentClass.getConstructor();
        return null;
    }

    public void setDestructor(MethodInfo dtor) { this.destructor = dtor; }
    public MethodInfo getDestructor() {
        if (destructor != null) return destructor;
        if (parentClass != null) return parentClass.getDestructor();
        return null;
    }

    // ----------------------------------------------------------------
    // Interface check
    // ----------------------------------------------------------------

    public boolean implementsInterface(String iface) {
        for (String i : implementedInterfaces) {
            if (i.equalsIgnoreCase(iface)) return true;
        }
        if (parentClass != null) return parentClass.implementsInterface(iface);
        return false;
    }
}
