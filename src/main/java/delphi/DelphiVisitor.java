package delphi;

import org.antlr.v4.runtime.tree.*;
import java.util.*;

/**
 * Tree-walking interpreter for the Delphi subset grammar.
 */
public class DelphiVisitor extends delphiBaseVisitor<Object> {

    // ----------------------------------------------------------------
    // Global state
    // ----------------------------------------------------------------

    private final Map<String, ClassDefinition> classes = new LinkedHashMap<>();
    private final Map<String, List<String>> interfaces = new LinkedHashMap<>();
    private final Map<String, delphiParser.ProcedureOrFunctionDeclarationContext> functions
            = new LinkedHashMap<>();

    private Environment globalEnv = new Environment();
    private Environment currentEnv = globalEnv;

    /** Sentinel: returned by callBuiltin when the name is not a built-in */
    private static final Object UNRESOLVED = new Object();

    // ================================================================
    // PROGRAM / BLOCK
    // ================================================================

    @Override
    public Object visitProgram(delphiParser.ProgramContext ctx) {
        visit(ctx.block());
        return null;
    }

    @Override
    public Object visitBlock(delphiParser.BlockContext ctx) {
        for (delphiParser.DeclarationPartContext dp : ctx.declarationPart()) visit(dp);
        if (ctx.compoundStatement() != null) visit(ctx.compoundStatement());
        return null;
    }

    @Override
    public Object visitDeclarationPart(delphiParser.DeclarationPartContext ctx) {
        return visitChildren(ctx);
    }

    // ================================================================
    // TYPE DEFINITIONS
    // ================================================================

    @Override
    public Object visitTypeDefinitionPart(delphiParser.TypeDefinitionPartContext ctx) {
        for (delphiParser.TypeDefinitionContext td : ctx.typeDefinition()) visit(td);
        return null;
    }

    @Override
    public Object visitTypeDefinition(delphiParser.TypeDefinitionContext ctx) {
        String typeName = ctx.identifier().getText();
        if (ctx.classType() != null)          registerClassType(typeName, ctx.classType());
        else if (ctx.interfaceType() != null) interfaces.put(typeName.toLowerCase(), new ArrayList<>());
        return null;
    }

    private void registerClassType(String className, delphiParser.ClassTypeContext ctc) {
        ClassDefinition classDef = new ClassDefinition(className);

        if (ctc.identifierList() != null) {
            for (delphiParser.IdentifierContext pid : ctc.identifierList().identifier()) {
                String pn = pid.getText();
                ClassDefinition parent = classes.get(pn.toLowerCase());
                if (parent != null) classDef.setParentClass(parent);
                else classDef.addInterface(pn);
            }
        }

        if (ctc.classBody() != null) {
            ClassDefinition.Visibility vis = ClassDefinition.Visibility.PUBLIC;
            for (delphiParser.ClassSectionContext sec : ctc.classBody().classSection()) {
                if (sec.visibilitySpecifier() != null)
                    vis = ClassDefinition.Visibility.valueOf(
                            sec.visibilitySpecifier().getText().toUpperCase());
                if (sec.classMemberList() != null)
                    for (delphiParser.ClassMemberContext mem : sec.classMemberList().classMember())
                        registerClassMember(classDef, mem, vis);
            }
        }
        classes.put(className.toLowerCase(), classDef);
    }

    private void registerClassMember(ClassDefinition classDef,
                                     delphiParser.ClassMemberContext mem,
                                     ClassDefinition.Visibility vis) {
        if (mem.fieldDeclaration() != null) {
            delphiParser.FieldDeclarationContext fd = mem.fieldDeclaration();
            String typeName = fd.typeIdentifier().getText();
            for (delphiParser.IdentifierContext id : fd.identifierList().identifier())
                classDef.addField(new ClassDefinition.FieldInfo(id.getText(), typeName, vis));

        } else if (mem.methodDeclaration() != null) {
            delphiParser.MethodDeclarationContext md = mem.methodDeclaration();
            String retType = md.typeIdentifier() != null ? md.typeIdentifier().getText() : null;
            List<ClassDefinition.ParameterInfo> params = parseParamList(md.formalParameterList());
            boolean isVirtual = false, isOverride = false, isAbstract = false;
            for (delphiParser.MethodDirectiveContext dir : md.methodDirective()) {
                String d = dir.getText().toLowerCase();
                if (d.equals("virtual"))  isVirtual  = true;
                if (d.equals("override")) isOverride = true;
                if (d.equals("abstract")) isAbstract = true;
            }
            ClassDefinition.MethodInfo mi = new ClassDefinition.MethodInfo(
                    md.identifier().getText(), params, retType, vis, null, classDef.getName());
            mi.isVirtual = isVirtual; mi.isOverride = isOverride; mi.isAbstract = isAbstract;
            classDef.addMethod(mi);

        } else if (mem.constructorDeclaration() != null) {
            delphiParser.ConstructorDeclarationContext cd = mem.constructorDeclaration();
            classDef.setConstructor(new ClassDefinition.MethodInfo(
                    cd.identifier().getText(), parseParamList(cd.formalParameterList()),
                    null, vis, null, classDef.getName()));

        } else if (mem.destructorDeclaration() != null) {
            delphiParser.DestructorDeclarationContext dd = mem.destructorDeclaration();
            classDef.setDestructor(new ClassDefinition.MethodInfo(
                    dd.identifier().getText(), new ArrayList<>(),
                    null, vis, null, classDef.getName()));
        }
    }

    // ================================================================
    // PROCEDURE / FUNCTION / METHOD DECLARATIONS
    // ================================================================

    @Override
    public Object visitProcedureAndFunctionDeclarationPart(
            delphiParser.ProcedureAndFunctionDeclarationPartContext ctx) {
        return visit(ctx.procedureOrFunctionDeclaration());
    }

    @Override
    public Object visitProcedureOrFunctionDeclaration(
            delphiParser.ProcedureOrFunctionDeclarationContext ctx) {
        if (ctx.procedureDeclaration() != null) {
            functions.put(ctx.procedureDeclaration().identifier().getText().toLowerCase(), ctx);
        } else if (ctx.functionDeclaration() != null) {
            functions.put(ctx.functionDeclaration().identifier().getText().toLowerCase(), ctx);
        } else if (ctx.constructorImplementation() != null) {
            delphiParser.ConstructorImplementationContext ci = ctx.constructorImplementation();
            registerMethodImpl(ci.identifier(0).getText(), ci.identifier(1).getText(),
                    ci.formalParameterList(), ci.block(), true, false);
        } else if (ctx.destructorImplementation() != null) {
            delphiParser.DestructorImplementationContext di = ctx.destructorImplementation();
            registerMethodImpl(di.identifier(0).getText(), di.identifier(1).getText(),
                    null, di.block(), false, true);
        } else if (ctx.methodImplementation() != null) {
            delphiParser.MethodImplementationContext mi = ctx.methodImplementation();
            registerMethodImpl(mi.identifier(0).getText(), mi.identifier(1).getText(),
                    mi.formalParameterList(), mi.block(), false, false);
        }
        return null;
    }

    private void registerMethodImpl(String className, String methodName,
                                    delphiParser.FormalParameterListContext paramCtx,
                                    delphiParser.BlockContext blockCtx,
                                    boolean isCtor, boolean isDtor) {
        ClassDefinition classDef = classes.get(className.toLowerCase());
        if (classDef == null) return;
        List<ClassDefinition.ParameterInfo> params = parseParamList(paramCtx);

        if (isCtor) {
            ClassDefinition.MethodInfo ctor = classDef.getConstructor();
            if (ctor == null) {
                ctor = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.setConstructor(ctor);
            } else { ctor.body = blockCtx; ctor.params = params; }
        } else if (isDtor) {
            ClassDefinition.MethodInfo dtor = classDef.getDestructor();
            if (dtor == null) {
                dtor = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.setDestructor(dtor);
            } else { dtor.body = blockCtx; }
        } else {
            ClassDefinition.MethodInfo method = classDef.getMethod(methodName);
            if (method == null) {
                method = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.addMethod(method);
            } else { method.body = blockCtx; method.params = params; }
        }
    }

    // ================================================================
    // VARIABLE / CONSTANT DECLARATIONS
    // ================================================================

    @Override
    public Object visitVariableDeclarationPart(delphiParser.VariableDeclarationPartContext ctx) {
        for (delphiParser.VariableDeclarationContext vd : ctx.variableDeclaration()) {
            String typeName = vd.type_().getText();
            for (delphiParser.IdentifierContext id : vd.identifierList().identifier())
                currentEnv.define(id.getText(), defaultValue(typeName));
        }
        return null;
    }

    private Object defaultValue(String t) {
        switch (t.toLowerCase()) {
            case "integer": case "int": return 0;
            case "real":    return 0.0;
            case "boolean": return false;
            case "string":  return "";
            case "char":    return '\0';
            default:        return null;
        }
    }

    @Override
    public Object visitConstantDefinitionPart(delphiParser.ConstantDefinitionPartContext ctx) {
        for (delphiParser.ConstantDefinitionContext cd : ctx.constantDefinition())
            currentEnv.define(cd.identifier().getText(), visit(cd.constant()));
        return null;
    }

    @Override
    public Object visitConstant(delphiParser.ConstantContext ctx) {
        boolean neg = ctx.sign() != null && ctx.sign().MINUS() != null;
        Object val;
        if      (ctx.unsignedNumber() != null) val = visit(ctx.unsignedNumber());
        else if (ctx.string()         != null) val = visit(ctx.string());
        else if (ctx.constantChr()    != null) val = visit(ctx.constantChr());
        else if (ctx.identifier()     != null) val = currentEnv.get(ctx.identifier().getText());
        else return null;
        return neg ? negate(val) : val;
    }

    @Override
    public Object visitConstantChr(delphiParser.ConstantChrContext ctx) {
        return String.valueOf((char) Integer.parseInt(ctx.unsignedInteger().NUM_INT().getText()));
    }

    // ================================================================
    // STATEMENTS
    // ================================================================

    @Override
    public Object visitCompoundStatement(delphiParser.CompoundStatementContext ctx) {
        return visit(ctx.statements());
    }

    @Override
    public Object visitStatements(delphiParser.StatementsContext ctx) {
        for (delphiParser.StatementContext st : ctx.statement()) visit(st);
        return null;
    }

    @Override
    public Object visitStatement(delphiParser.StatementContext ctx) {
        if (ctx.simpleStatement()     != null) return visit(ctx.simpleStatement());
        if (ctx.structuredStatement() != null) return visit(ctx.structuredStatement());
        return null;
    }

    /**
     * simpleStatement alternatives (no labels in grammar):
     *   1) designator ASSIGN expression
     *   2) designator
     *   3) GOTO label
     * Detected by checking which tokens are present.
     */
    @Override
    public Object visitSimpleStatement(delphiParser.SimpleStatementContext ctx) {
        if (ctx.GOTO() != null) throw new RuntimeException("GOTO not supported");

        if (ctx.ASSIGN() != null) {
            // Assignment: designator := expression
            Object rhs = visit(ctx.expression());
            assignDesignator(ctx.designator(), rhs);
            return null;
        }

        // Standalone call/expression
        evalDesignator(ctx.designator());
        return null;
    }

    // ================================================================
    // DESIGNATOR: assignment (left-hand side)
    // ================================================================

    private void assignDesignator(delphiParser.DesignatorContext ctx, Object value) {
        List<delphiParser.DesignatorSuffixContext> suffixes = ctx.designatorSuffix();
        String baseName = ctx.primary().identifier().getText();

        if (suffixes.isEmpty()) {
            assignSimple(baseName, value);
            return;
        }

        // Navigate to the object holding the final field
        Object base = evalPrimary(ctx.primary());
        for (int i = 0; i < suffixes.size() - 1; i++)
            base = applySuffix(base, suffixes.get(i));

        // Apply last suffix as write
        delphiParser.DesignatorSuffixContext last = suffixes.get(suffixes.size() - 1);
        if (last.DOT() != null) {
            String field = last.identifier().getText();
            if (base instanceof DelphiObject) {
                ((DelphiObject) base).setField(field, value);
                return;
            }
        }
        // Fallback: treat as simple variable
        assignSimple(baseName, value);
    }

    private void assignSimple(String name, Object value) {
        // Check implicit self fields first
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            if (self.hasField(name)) { self.setField(name, value); return; }
        }
        currentEnv.assign(name, value);
    }

    // ================================================================
    // DESIGNATOR: read / call (right-hand side and standalone)
    // ================================================================

    private Object evalDesignator(delphiParser.DesignatorContext ctx) {
        String name = ctx.primary().identifier().getText();
        List<delphiParser.DesignatorSuffixContext> suffixes = ctx.designatorSuffix();

        // ---- Object creation: ClassName.Create(args) ----
        if (!suffixes.isEmpty() && suffixes.get(0).DOT() != null) {
            String member = suffixes.get(0).identifier().getText();
            if (member.equalsIgnoreCase("create")) {
                ClassDefinition classDef = classes.get(name.toLowerCase());
                if (classDef != null) {
                    List<Object> args = new ArrayList<>();
                    if (suffixes.size() >= 2 && suffixes.get(1).LPAREN() != null)
                        args = evalArgList(suffixes.get(1).parameterList());
                    return instantiateClass(classDef, args);
                }
            }
        }

        // ---- Single call suffix: foo(args) ----
        if (suffixes.size() == 1 && suffixes.get(0).LPAREN() != null) {
            List<Object> args = evalArgList(suffixes.get(0).parameterList());
            return callNamed(name, args);
        }

        // ---- No suffixes: plain name ----
        if (suffixes.isEmpty()) return resolveName(name);

        // ---- General chain ----
        Object base = evalPrimary(ctx.primary());
        for (delphiParser.DesignatorSuffixContext suffix : suffixes)
            base = applySuffix(base, suffix);
        return base;
    }

    /**
     * Apply one designator suffix to 'base'.
     */
    private Object applySuffix(Object base, delphiParser.DesignatorSuffixContext suffix) {
        if (suffix.DOT() != null) {
            String member = suffix.identifier().getText();
            if (base instanceof DelphiObject) {
                DelphiObject obj = (DelphiObject) base;
                if (obj.hasField(member)) return obj.getField(member);
                return new MethodRef(obj, member);  // defer call
            }
            throw new RuntimeException("Cannot access '." + member + "' on: " + base);

        } else if (suffix.LPAREN() != null) {
            List<Object> args = evalArgList(suffix.parameterList());

            if (base instanceof MethodRef) {
                MethodRef ref = (MethodRef) base;
                String lname = ref.methodName.toLowerCase();
                if (lname.equals("free") || lname.equals("destroy")) {
                    ClassDefinition.MethodInfo dtor = ref.obj.getClassDef().getDestructor();
                    if (dtor != null && dtor.body != null)
                        callMethod(ref.obj, dtor, new ArrayList<>());
                    return null;
                }
                ClassDefinition.MethodInfo method = ref.obj.getClassDef().getMethod(ref.methodName);
                if (method != null && method.body != null) return callMethod(ref.obj, method, args);
                System.err.println("Warning: method '" + ref.methodName + "' not found or has no body");
                return null;
            }
            System.err.println("Warning: call suffix on non-callable: " + base);
            return null;

        } else if (suffix.LBRACKET() != null) {
            int idx = toInt(visit(suffix.expression(0)));
            if (base instanceof Object[]) return ((Object[]) base)[idx];
            throw new RuntimeException("Cannot index: " + base);

        } else if (suffix.POINTER() != null) {
            return base;
        }
        return base;
    }

    private Object evalPrimary(delphiParser.PrimaryContext ctx) {
        return resolveName(ctx.identifier().getText());
    }

    private Object resolveName(String name) {
        if (name.equalsIgnoreCase("self") && currentEnv.has("__self__"))
            return currentEnv.get("__self__");
        if (currentEnv.has(name)) return currentEnv.get(name);
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            if (self.hasField(name)) return self.getField(name);
        }
        if (functions.containsKey(name.toLowerCase()))
            return callUserFunction(functions.get(name.toLowerCase()), new ArrayList<>());
        return null;
    }

    private Object callNamed(String name, List<Object> args) {
        Object b = callBuiltin(name, args);
        if (b != UNRESOLVED) return b;
        if (functions.containsKey(name.toLowerCase()))
            return callUserFunction(functions.get(name.toLowerCase()), args);
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            ClassDefinition.MethodInfo method = self.getClassDef().getMethod(name);
            if (method != null && method.body != null) return callMethod(self, method, args);
        }
        System.err.println("Warning: unknown function/procedure '" + name + "'");
        return null;
    }

    private List<Object> evalArgList(delphiParser.ParameterListContext ctx) {
        List<Object> args = new ArrayList<>();
        if (ctx == null) return args;
        for (delphiParser.ActualParameterContext ap : ctx.actualParameter())
            args.add(visit(ap.expression(0)));
        return args;
    }

    // ================================================================
    // FACTOR (expression context)
    // ================================================================

    @Override
    public Object visitFactor(delphiParser.FactorContext ctx) {
        if (ctx.LPAREN() != null && ctx.expression() != null) return visit(ctx.expression());
        if (ctx.NOT()    != null) return !isTruthy(visit(ctx.factor()));
        if (ctx.bool_()  != null) return visit(ctx.bool_());
        if (ctx.NIL()    != null) return null;
        if (ctx.unsignedConstant() != null) return visit(ctx.unsignedConstant());
        if (ctx.set_()   != null) return visit(ctx.set_());
        if (ctx.designator() != null) return evalDesignator(ctx.designator());
        return null;
    }

    // ================================================================
    // BUILT-IN FUNCTIONS / PROCEDURES
    // ================================================================

    private Object callBuiltin(String name, List<Object> args) {
        switch (name.toLowerCase()) {
            case "writeln": {
                StringBuilder sb = new StringBuilder();
                for (Object a : args) sb.append(fmt(a));
                System.out.println(sb);
                return null;
            }
            case "write": {
                StringBuilder sb = new StringBuilder();
                for (Object a : args) sb.append(fmt(a));
                System.out.print(sb);
                return null;
            }
            case "readln": { Scanner sc = new Scanner(System.in); return sc.nextLine(); }
            case "read":   { Scanner sc = new Scanner(System.in); return sc.nextLine(); }
            case "inttostr":   return args.isEmpty() ? "" : String.valueOf(toInt(args.get(0)));
            case "strtoint":   return args.isEmpty() ? 0 : Integer.parseInt(args.get(0).toString().trim());
            case "length":     return args.isEmpty() ? 0 : args.get(0).toString().length();
            case "copy": {
                if (args.size() >= 3) {
                    String s = args.get(0).toString();
                    int from = Math.max(0, toInt(args.get(1)) - 1);
                    int len  = toInt(args.get(2));
                    return s.substring(from, Math.min(from + len, s.length()));
                }
                return "";
            }
            case "pos": {
                if (args.size() >= 2) {
                    int idx = args.get(1).toString().indexOf(args.get(0).toString());
                    return idx < 0 ? 0 : idx + 1;
                }
                return 0;
            }
            case "upcase": case "uppercase":
                return args.isEmpty() ? "" : args.get(0).toString().toUpperCase();
            case "lowercase":
                return args.isEmpty() ? "" : args.get(0).toString().toLowerCase();
            case "abs":
                if (!args.isEmpty()) {
                    Object v = args.get(0);
                    return v instanceof Double ? Math.abs((Double) v) : Math.abs(toInt(v));
                }
                return 0;
            case "sqr":  { double v = args.isEmpty() ? 0 : toDouble(args.get(0)); return v * v; }
            case "sqrt": return args.isEmpty() ? 0.0 : Math.sqrt(toDouble(args.get(0)));
            case "trunc":return args.isEmpty() ? 0 : (int) toDouble(args.get(0));
            case "round":return args.isEmpty() ? 0 : (int) Math.round(toDouble(args.get(0)));
            case "odd":  return !args.isEmpty() && toInt(args.get(0)) % 2 != 0;
            case "succ": return args.isEmpty() ? 0 : toInt(args.get(0)) + 1;
            case "pred": return args.isEmpty() ? 0 : toInt(args.get(0)) - 1;
            case "ord":
                if (!args.isEmpty()) {
                    Object v = args.get(0);
                    if (v instanceof Character) return (int)(Character) v;
                    if (v instanceof String && !((String) v).isEmpty())
                        return (int)((String) v).charAt(0);
                }
                return 0;
            case "chr":  return args.isEmpty() ? '\0' : (char) toInt(args.get(0));
            case "str":  return args.isEmpty() ? "" : String.valueOf(args.get(0));
            case "high": return Integer.MAX_VALUE;
            case "low":  return Integer.MIN_VALUE;
            case "free": case "destroy": return null;
            default:     return UNRESOLVED;
        }
    }

    private String fmt(Object o) {
        if (o == null) return "nil";
        if (o instanceof Boolean) return (Boolean) o ? "TRUE" : "FALSE";
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return o.toString();
    }

    // ================================================================
    // FUNCTION / METHOD INVOCATION
    // ================================================================

    private Object callUserFunction(
            delphiParser.ProcedureOrFunctionDeclarationContext ctx, List<Object> args) {
        Environment saved = currentEnv;
        Environment fnEnv = new Environment(globalEnv);
        currentEnv = fnEnv;
        try {
            if (ctx.procedureDeclaration() != null) {
                delphiParser.ProcedureDeclarationContext pd = ctx.procedureDeclaration();
                bindParams(pd.formalParameterList(), args, fnEnv);
                visit(pd.block());
                return null;
            } else {
                delphiParser.FunctionDeclarationContext fd = ctx.functionDeclaration();
                String fname = fd.identifier().getText().toLowerCase();
                fnEnv.define(fname, null);
                fnEnv.define("result", null);
                bindParams(fd.formalParameterList(), args, fnEnv);
                try { visit(fd.block()); } catch (ReturnException re) { return re.getValue(); }
                Object res = fnEnv.get("result");
                if (res == null) res = fnEnv.get(fname);
                return res;
            }
        } finally { currentEnv = saved; }
    }

    private Object callMethod(DelphiObject self, ClassDefinition.MethodInfo method, List<Object> args) {
        Environment saved = currentEnv;
        Environment env   = new Environment(globalEnv);
        currentEnv = env;
        env.define("__self__", self);
        env.define("self", self);
        try {
            bindParamInfos(method.params, args, env);
            if (method.returnType != null) {
                env.define("result", defaultValue(method.returnType));
                env.define(method.name.toLowerCase(), null);
            }
            if (method.body != null)
                try { visitBlock((delphiParser.BlockContext) method.body); }
                catch (ReturnException re) { return re.getValue(); }
            return method.returnType != null ? env.get("result") : null;
        } finally { currentEnv = saved; }
    }

    private void bindParams(delphiParser.FormalParameterListContext ctx,
                            List<Object> args, Environment env) {
        bindParamInfos(parseParamList(ctx), args, env);
    }

    private void bindParamInfos(List<ClassDefinition.ParameterInfo> params,
                                 List<Object> args, Environment env) {
        for (int i = 0; i < params.size(); i++)
            env.define(params.get(i).name, i < args.size() ? args.get(i) : null);
    }

    private List<ClassDefinition.ParameterInfo> parseParamList(
            delphiParser.FormalParameterListContext ctx) {
        List<ClassDefinition.ParameterInfo> params = new ArrayList<>();
        if (ctx == null) return params;
        for (delphiParser.FormalParameterSectionContext fps : ctx.formalParameterSection()) {
            boolean isVar = fps.VAR() != null;
            delphiParser.ParameterGroupContext pg = fps.parameterGroup();
            if (pg == null) continue;
            String typeName = pg.typeIdentifier().getText();
            for (delphiParser.IdentifierContext id : pg.identifierList().identifier())
                params.add(new ClassDefinition.ParameterInfo(id.getText(), typeName, isVar));
        }
        return params;
    }

    // ================================================================
    // OBJECT INSTANTIATION
    // ================================================================

    private Object instantiateClass(ClassDefinition classDef, List<Object> args) {
        DelphiObject obj = new DelphiObject(classDef);
        ClassDefinition.MethodInfo ctor = classDef.getConstructor();
        if (ctor != null && ctor.body != null) callMethod(obj, ctor, args);
        return obj;
    }

    // ================================================================
    // STRUCTURED STATEMENTS
    // ================================================================

    @Override
    public Object visitStructuredStatement(delphiParser.StructuredStatementContext ctx) {
        if (ctx.compoundStatement()    != null) return visit(ctx.compoundStatement());
        if (ctx.conditionalStatement() != null) return visit(ctx.conditionalStatement());
        if (ctx.repetetiveStatement()  != null) return visit(ctx.repetetiveStatement());
        if (ctx.withStatement()        != null) return visit(ctx.withStatement());
        if (ctx.tryStatement()         != null) return visit(ctx.tryStatement());
        return null;
    }

    @Override
    public Object visitIfStatement(delphiParser.IfStatementContext ctx) {
        if (isTruthy(visit(ctx.expression()))) visit(ctx.statement(0));
        else if (ctx.ELSE() != null)           visit(ctx.statement(1));
        return null;
    }

    @Override
    public Object visitWhileStatement(delphiParser.WhileStatementContext ctx) {
        while (isTruthy(visit(ctx.expression()))) visit(ctx.statement());
        return null;
    }

    @Override
    public Object visitRepeatStatement(delphiParser.RepeatStatementContext ctx) {
        do { visit(ctx.statements()); } while (!isTruthy(visit(ctx.expression())));
        return null;
    }

    @Override
    public Object visitForStatement(delphiParser.ForStatementContext ctx) {
        String var  = ctx.identifier().getText();
        int    from = toInt(visit(ctx.forList().initialValue().expression()));
        int    to   = toInt(visit(ctx.forList().finalValue().expression()));
        boolean dn  = ctx.forList().DOWNTO() != null;
        if (!dn) for (int i = from; i <= to; i++) { currentEnv.assign(var, i); visit(ctx.statement()); }
        else     for (int i = from; i >= to; i--) { currentEnv.assign(var, i); visit(ctx.statement()); }
        return null;
    }

    @Override
    public Object visitCaseStatement(delphiParser.CaseStatementContext ctx) {
        Object val = visit(ctx.expression());
        for (delphiParser.CaseListElementContext elem : ctx.caseListElement())
            for (delphiParser.ConstantContext c : elem.constList().constant())
                if (equal(val, visit(c))) { visit(elem.statement()); return null; }
        if (ctx.statements() != null) visit(ctx.statements());
        return null;
    }

    @Override
    public Object visitTryStatement(delphiParser.TryStatementContext ctx) {
        try     { visit(ctx.statements()); }
        catch (Exception e) { if (ctx.exceptBlock()  != null) visit(ctx.exceptBlock()); }
        finally             { if (ctx.finallyBlock() != null) visit(ctx.finallyBlock()); }
        return null;
    }

    @Override
    public Object visitWithStatement(delphiParser.WithStatementContext ctx) {
        return visit(ctx.statement());
    }

    // ================================================================
    // EXPRESSIONS
    // ================================================================

    @Override
    public Object visitExpression(delphiParser.ExpressionContext ctx) {
        Object left = visit(ctx.simpleExpression(0));
        if (ctx.relationalOperator() == null) return left;
        Object right = visit(ctx.simpleExpression(1));
        switch (ctx.relationalOperator().getText()) {
            case "=":  return equal(left, right);
            case "<>": return !equal(left, right);
            case "<":  return compare(left, right) < 0;
            case "<=": return compare(left, right) <= 0;
            case ">":  return compare(left, right) > 0;
            case ">=": return compare(left, right) >= 0;
            default:   return false;
        }
    }

    @Override
    public Object visitSimpleExpression(delphiParser.SimpleExpressionContext ctx) {
        Object result = visit(ctx.term(0));
        if (ctx.sign() != null && ctx.sign().MINUS() != null) result = negate(result);
        for (int i = 0; i < ctx.additiveOperator().size(); i++) {
            Object right = visit(ctx.term(i + 1));
            switch (ctx.additiveOperator(i).getText().toLowerCase()) {
                case "+":   result = add(result, right); break;
                case "-":   result = subtract(result, right); break;
                case "or":  result = isTruthy(result) || isTruthy(right); break;
                case "xor": result = isTruthy(result) ^  isTruthy(right); break;
            }
        }
        return result;
    }

    @Override
    public Object visitTerm(delphiParser.TermContext ctx) {
        Object result = visit(ctx.signedFactor(0));
        for (int i = 0; i < ctx.multiplicativeOperator().size(); i++) {
            Object right = visit(ctx.signedFactor(i + 1));
            switch (ctx.multiplicativeOperator(i).getText().toLowerCase()) {
                case "*":   result = multiply(result, right); break;
                case "/":   result = divide(result, right);   break;
                case "div": result = intDivide(result, right);break;
                case "mod": result = mod(result, right);      break;
                case "and": result = isTruthy(result) && isTruthy(right); break;
                case "shl": result = toInt(result) << toInt(right); break;
                case "shr": result = toInt(result) >> toInt(right); break;
            }
        }
        return result;
    }

    @Override
    public Object visitSignedFactor(delphiParser.SignedFactorContext ctx) {
        Object val = visit(ctx.factor());
        return (ctx.sign() != null && ctx.sign().MINUS() != null) ? negate(val) : val;
    }

    // ================================================================
    // LITERALS
    // ================================================================

    @Override
    public Object visitUnsignedConstant(delphiParser.UnsignedConstantContext ctx) {
        if (ctx.unsignedNumber() != null) return visit(ctx.unsignedNumber());
        if (ctx.string()         != null) return visit(ctx.string());
        return null;
    }

    @Override
    public Object visitUnsignedNumber(delphiParser.UnsignedNumberContext ctx) {
        if (ctx.unsignedInteger() != null) return visit(ctx.unsignedInteger());
        if (ctx.unsignedReal()    != null) return visit(ctx.unsignedReal());
        return 0;
    }

    @Override
    public Object visitUnsignedInteger(delphiParser.UnsignedIntegerContext ctx) {
        return Integer.parseInt(ctx.NUM_INT().getText());
    }

    @Override
    public Object visitUnsignedReal(delphiParser.UnsignedRealContext ctx) {
        return Double.parseDouble(ctx.NUM_REAL().getText());
    }

    @Override
    public Object visitString(delphiParser.StringContext ctx) {
        String raw = ctx.STRING_LITERAL().getText();
        return raw.substring(1, raw.length() - 1).replace("''", "'");
    }

    @Override
    public Object visitBool_(delphiParser.Bool_Context ctx) {
        return ctx.TRUE() != null;
    }

    // ================================================================
    // ARITHMETIC / COMPARISON HELPERS
    // ================================================================

    private Object add(Object a, Object b) {
        if (a instanceof String || b instanceof String) return fmt(a) + fmt(b);
        if (a instanceof Double || b instanceof Double) return toDouble(a) + toDouble(b);
        return toInt(a) + toInt(b);
    }
    private Object subtract(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return toDouble(a) - toDouble(b);
        return toInt(a) - toInt(b);
    }
    private Object multiply(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return toDouble(a) * toDouble(b);
        return toInt(a) * toInt(b);
    }
    private Object divide(Object a, Object b)    { return toDouble(a) / toDouble(b); }
    private Object intDivide(Object a, Object b) { return toInt(a) / toInt(b); }
    private Object mod(Object a, Object b)       { return toInt(a) % toInt(b); }
    private Object negate(Object a) {
        if (a instanceof Double) return -(Double) a;
        return -toInt(a);
    }
    private boolean equal(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) return toDouble(a) == toDouble(b);
        return a.equals(b);
    }
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) return Double.compare(toDouble(a), toDouble(b));
        return a.toString().compareTo(b.toString());
    }
    private boolean isTruthy(Object val) {
        if (val == null)            return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Integer) return (Integer) val != 0;
        if (val instanceof Double)  return (Double)  val != 0.0;
        if (val instanceof String)  return !((String) val).isEmpty();
        return true;
    }
    private int toInt(Object val) {
        if (val == null)            return 0;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Double)  return ((Double) val).intValue();
        if (val instanceof Boolean) return (Boolean) val ? 1 : 0;
        if (val instanceof String)  { try { return Integer.parseInt(((String) val).trim()); } catch (NumberFormatException e) { return 0; } }
        return 0;
    }
    private double toDouble(Object val) {
        if (val == null)            return 0.0;
        if (val instanceof Double)  return (Double) val;
        if (val instanceof Integer) return ((Integer) val).doubleValue();
        if (val instanceof Boolean) return (Boolean) val ? 1.0 : 0.0;
        if (val instanceof String)  { try { return Double.parseDouble((String) val); } catch (NumberFormatException e) { return 0.0; } }
        return 0.0;
    }

    // ================================================================
    // MethodRef — holds obj + method name until call suffix is applied
    // ================================================================

    private static class MethodRef {
        final DelphiObject obj;
        final String methodName;
        MethodRef(DelphiObject obj, String methodName) {
            this.obj = obj;
            this.methodName = methodName;
        }
    }
}