package delphi;

import org.antlr.v4.runtime.tree.*;
import java.util.*;

/**
 * Tree-walking interpreter for the Delphi subset grammar.
 * Walks the ANTLR ParseTree produced by delphiParser and evaluates it.
 */
public class DelphiVisitor extends delphiBaseVisitor<Object> {

    // ----------------------------------------------------------------
    // Global state
    // ----------------------------------------------------------------

    /** Class registry */
    private final Map<String, ClassDefinition> classes = new LinkedHashMap<>();

    /** Interface registry */
    private final Map<String, List<String>> interfaces = new LinkedHashMap<>();

    /** Global procedure/function registry */
    private final Map<String, delphiParser.ProcedureOrFunctionDeclarationContext> functions = new LinkedHashMap<>();

    /** Global environment */
    private Environment globalEnv = new Environment();

    /** Current environment (changes as we enter/exit scopes) */
    private Environment currentEnv = globalEnv;

    // ----------------------------------------------------------------
    // Helper: push/pop scope
    // ----------------------------------------------------------------

    private Environment pushScope() {
        Environment child = new Environment(currentEnv);
        currentEnv = child;
        return child;
    }

    private void popScope(Environment saved) {
        currentEnv = saved;
    }

    // ================================================================
    // PROGRAM
    // ================================================================

    @Override
    public Object visitProgram(delphiParser.ProgramContext ctx) {
        // First pass: collect all class and function declarations from blocks
        visit(ctx.block());
        return null;
    }

    @Override
    public Object visitBlock(delphiParser.BlockContext ctx) {
        // First pass: register all declarations
        for (delphiParser.DeclarationPartContext dp : ctx.declarationPart()) {
            visit(dp);
        }
        // Second pass: execute the compound statement
        if (ctx.compoundStatement() != null) {
            visit(ctx.compoundStatement());
        }
        return null;
    }

    @Override
    public Object visitDeclarationPart(delphiParser.DeclarationPartContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitTypeDefinitionPart(delphiParser.TypeDefinitionPartContext ctx) {
        for (delphiParser.TypeDefinitionContext td : ctx.typeDefinition()) {
            visit(td);
        }
        return null;
    }

    @Override
    public Object visitTypeDefinition(delphiParser.TypeDefinitionContext ctx) {
        String typeName = ctx.identifier().getText();
        if (ctx.classType() != null) {
            // It's a class definition
            registerClassType(typeName, ctx.classType());
        } else if (ctx.interfaceType() != null) {
            // It's an interface definition — register interface name
            interfaces.put(typeName.toLowerCase(), new ArrayList<>());
        }
        // Plain type aliases etc. — no action needed for interpreter
        return null;
    }

    private void registerClassType(String className, delphiParser.ClassTypeContext ctc) {
        ClassDefinition classDef = new ClassDefinition(className);

        // Parse parent class / implemented interfaces from the identifier list
        if (ctc.identifierList() != null) {
            for (delphiParser.IdentifierContext pid : ctc.identifierList().identifier()) {
                String parentName = pid.getText();
                ClassDefinition parent = classes.get(parentName.toLowerCase());
                if (parent != null) {
                    classDef.setParentClass(parent);
                } else {
                    // Assume it's an interface
                    classDef.addInterface(parentName);
                }
            }
        }

        // Parse class body sections
        if (ctc.classBody() != null) {
            ClassDefinition.Visibility currentVis = ClassDefinition.Visibility.PUBLIC;
            for (delphiParser.ClassSectionContext sec : ctc.classBody().classSection()) {
                if (sec.visibilitySpecifier() != null) {
                    String visText = sec.visibilitySpecifier().getText().toUpperCase();
                    currentVis = ClassDefinition.Visibility.valueOf(visText);
                }
                if (sec.classMemberList() != null) {
                    for (delphiParser.ClassMemberContext mem : sec.classMemberList().classMember()) {
                        registerClassMember(classDef, mem, currentVis);
                    }
                }
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
            for (delphiParser.IdentifierContext id : fd.identifierList().identifier()) {
                classDef.addField(new ClassDefinition.FieldInfo(id.getText(), typeName, vis));
            }
        } else if (mem.methodDeclaration() != null) {
            delphiParser.MethodDeclarationContext md = mem.methodDeclaration();
            String mName = md.identifier().getText();
            String retType = md.typeIdentifier() != null ? md.typeIdentifier().getText() : null;
            List<ClassDefinition.ParameterInfo> params = parseParamList(md.formalParameterList());
            boolean isVirtual = false, isOverride = false, isAbstract = false;
            for (delphiParser.MethodDirectiveContext dir : md.methodDirective()) {
                String d = dir.getText().toLowerCase();
                if (d.equals("virtual")) isVirtual = true;
                if (d.equals("override")) isOverride = true;
                if (d.equals("abstract")) isAbstract = true;
            }
            ClassDefinition.MethodInfo mi = new ClassDefinition.MethodInfo(mName, params, retType, vis, null, classDef.getName());
            mi.isVirtual = isVirtual;
            mi.isOverride = isOverride;
            mi.isAbstract = isAbstract;
            classDef.addMethod(mi);
        } else if (mem.constructorDeclaration() != null) {
            delphiParser.ConstructorDeclarationContext cd = mem.constructorDeclaration();
            String cName = cd.identifier().getText();
            List<ClassDefinition.ParameterInfo> params = parseParamList(cd.formalParameterList());
            ClassDefinition.MethodInfo mi = new ClassDefinition.MethodInfo(cName, params, null, vis, null, classDef.getName());
            classDef.setConstructor(mi);
        } else if (mem.destructorDeclaration() != null) {
            delphiParser.DestructorDeclarationContext dd = mem.destructorDeclaration();
            String dName = dd.identifier().getText();
            List<ClassDefinition.ParameterInfo> params = new ArrayList<>();
            ClassDefinition.MethodInfo mi = new ClassDefinition.MethodInfo(dName, params, null, vis, null, classDef.getName());
            classDef.setDestructor(mi);
        }
    }

    // ================================================================
    // PROCEDURE / FUNCTION DECLARATIONS
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
            registerProc(ctx);
        } else if (ctx.functionDeclaration() != null) {
            registerProc(ctx);
        } else if (ctx.constructorImplementation() != null) {
            registerMethodImpl(ctx.constructorImplementation().identifier(0).getText(),
                               ctx.constructorImplementation().identifier(1).getText(),
                               ctx.constructorImplementation().formalParameterList(),
                               ctx.constructorImplementation().block(),
                               true, false);
        } else if (ctx.destructorImplementation() != null) {
            registerMethodImpl(ctx.destructorImplementation().identifier(0).getText(),
                               ctx.destructorImplementation().identifier(1).getText(),
                               null,
                               ctx.destructorImplementation().block(),
                               false, true);
        } else if (ctx.methodImplementation() != null) {
            registerMethodImpl(ctx.methodImplementation().identifier(0).getText(),
                               ctx.methodImplementation().identifier(1).getText(),
                               ctx.methodImplementation().formalParameterList(),
                               ctx.methodImplementation().block(),
                               false, false);
        }
        return null;
    }

    private void registerProc(delphiParser.ProcedureOrFunctionDeclarationContext ctx) {
        String name;
        if (ctx.procedureDeclaration() != null) {
            name = ctx.procedureDeclaration().identifier().getText();
        } else {
            name = ctx.functionDeclaration().identifier().getText();
        }
        functions.put(name.toLowerCase(), ctx);
    }

    private void registerMethodImpl(String className, String methodName,
                                    delphiParser.FormalParameterListContext paramCtx,
                                    delphiParser.BlockContext blockCtx,
                                    boolean isCtor, boolean isDtor) {
        ClassDefinition classDef = classes.get(className.toLowerCase());
        if (classDef == null) {
            // Might be a forward reference; store for later
            return;
        }
        List<ClassDefinition.ParameterInfo> params = parseParamList(paramCtx);
        if (isCtor) {
            ClassDefinition.MethodInfo ctor = classDef.getConstructor();
            if (ctor == null) {
                ctor = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.setConstructor(ctor);
            } else {
                ctor.body = blockCtx;
                ctor.params = params;
            }
        } else if (isDtor) {
            ClassDefinition.MethodInfo dtor = classDef.getDestructor();
            if (dtor == null) {
                dtor = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.setDestructor(dtor);
            } else {
                dtor.body = blockCtx;
            }
        } else {
            ClassDefinition.MethodInfo method = classDef.getMethod(methodName);
            if (method == null) {
                method = new ClassDefinition.MethodInfo(methodName, params, null,
                        ClassDefinition.Visibility.PUBLIC, blockCtx, className);
                classDef.addMethod(method);
            } else {
                method.body = blockCtx;
                method.params = params;
            }
        }
    }

    // ================================================================
    // VARIABLE DECLARATIONS
    // ================================================================

    @Override
    public Object visitVariableDeclarationPart(delphiParser.VariableDeclarationPartContext ctx) {
        for (delphiParser.VariableDeclarationContext vd : ctx.variableDeclaration()) {
            for (delphiParser.IdentifierContext id : vd.identifierList().identifier()) {
                currentEnv.define(id.getText(), defaultValue(vd.type_().getText()));
            }
        }
        return null;
    }

    private Object defaultValue(String typeName) {
        String t = typeName.toLowerCase();
        if (t.equals("integer") || t.equals("int")) return 0;
        if (t.equals("real")) return 0.0;
        if (t.equals("boolean")) return false;
        if (t.equals("string")) return "";
        if (t.equals("char")) return '\0';
        return null;
    }

    // ================================================================
    // CONSTANT DEFINITIONS
    // ================================================================

    @Override
    public Object visitConstantDefinitionPart(delphiParser.ConstantDefinitionPartContext ctx) {
        for (delphiParser.ConstantDefinitionContext cd : ctx.constantDefinition()) {
            String name = cd.identifier().getText();
            Object val = visit(cd.constant());
            currentEnv.define(name, val);
        }
        return null;
    }

    @Override
    public Object visitConstant(delphiParser.ConstantContext ctx) {
        if (ctx.unsignedNumber() != null) return visit(ctx.unsignedNumber());
        if (ctx.string() != null) return visit(ctx.string());
        if (ctx.identifier() != null) return currentEnv.get(ctx.identifier().getText());
        if (ctx.sign() != null) {
            Object val = ctx.unsignedNumber() != null ? visit(ctx.unsignedNumber())
                                                      : currentEnv.get(ctx.identifier().getText());
            if (ctx.sign().MINUS() != null) {
                if (val instanceof Integer) return -(Integer) val;
                if (val instanceof Double) return -(Double) val;
            }
            return val;
        }
        return null;
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
        for (delphiParser.StatementContext st : ctx.statement()) {
            visit(st);
        }
        return null;
    }

    @Override
    public Object visitStatement(delphiParser.StatementContext ctx) {
        if (ctx.simpleStatement() != null) return visit(ctx.simpleStatement());
        if (ctx.structuredStatement() != null) return visit(ctx.structuredStatement());
        return null;
    }

    @Override
    public Object visitSimpleStatement(delphiParser.SimpleStatementContext ctx) {
        if (ctx.assignmentStatement() != null) return visit(ctx.assignmentStatement());
        if (ctx.procedureStatement() != null) return visit(ctx.procedureStatement());
        if (ctx.gotoStatement() != null) throw new RuntimeException("GOTO not supported");
        return null;
    }

    // ----------------------------------------------------------------
    // Assignment
    // ----------------------------------------------------------------

    @Override
    public Object visitAssignmentStatement(delphiParser.AssignmentStatementContext ctx) {
        Object rhs = visit(ctx.expression());
        assignToVariable(ctx.variable(), rhs);
        return null;
    }

    private void assignToVariable(delphiParser.VariableContext ctx, Object value) {
        String baseName = (ctx.identifier() != null && !ctx.identifier().isEmpty())
                        ? ctx.identifier().get(0).getText()
                        : ctx.getStart().getText();

        // Check for field access: obj.field
        List<ParseTree> children = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) children.add(ctx.getChild(i));

        // Find DOT accesses
        if (children.size() >= 3) {
            // Build access chain
            Object obj = resolveVariableBase(ctx, 0);
            // Last identifier is the target field
            String fieldName = ctx.identifier(ctx.identifier().size() - 1).getText();
            if (obj instanceof DelphiObject) {
                ((DelphiObject) obj).setField(fieldName, value);
                return;
            }
        }

        // Check if it's self.field in method context
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            if (self.hasField(baseName)) {
                self.setField(baseName, value);
                return;
            }
        }

        currentEnv.assign(baseName, value);
    }

    private Object resolveVariableBase(delphiParser.VariableContext ctx, int depth) {
        // For multi-part variable like a.b.c, resolve prefix
        if (ctx.identifier().size() <= 1) {
            String name = ctx.identifier(0).getText();
            if (name.equalsIgnoreCase("self") && currentEnv.has("__self__")) {
                return currentEnv.get("__self__");
            }
            return currentEnv.get(name);
        }
        // Resolve first part, then navigate
        String firstName = ctx.identifier(0).getText();
        Object base;
        if (firstName.equalsIgnoreCase("self") && currentEnv.has("__self__")) {
            base = currentEnv.get("__self__");
        } else {
            base = currentEnv.get(firstName);
        }
        // Navigate through intermediate dots (all except last)
        for (int i = 1; i < ctx.identifier().size() - 1; i++) {
            String field = ctx.identifier(i).getText();
            if (base instanceof DelphiObject) {
                base = ((DelphiObject) base).getField(field);
            } else {
                throw new RuntimeException("Cannot access field '" + field + "' on non-object");
            }
        }
        return base;
    }

    // ----------------------------------------------------------------
    // Procedure/function call statement
    // ----------------------------------------------------------------

    @Override
    public Object visitProcedureStatement(delphiParser.ProcedureStatementContext ctx) {
        String name = ctx.identifier().getText();
        List<Object> args = new ArrayList<>();
        if (ctx.parameterList() != null) {
            for (delphiParser.ActualParameterContext ap : ctx.parameterList().actualParameter()) {
                args.add(visit(ap.expression(0)));
            }
        }
        return callBuiltinOrUserProc(name, args);
    }

    private Object callBuiltinOrUserProc(String name, List<Object> args) {
        String lname = name.toLowerCase();

        // Built-ins
        if (lname.equals("writeln") || lname.equals("write")) {
            StringBuilder sb = new StringBuilder();
            for (Object a : args) sb.append(objectToString(a));
            if (lname.equals("writeln")) System.out.println(sb);
            else System.out.print(sb);
            return null;
        }
        if (lname.equals("readln") || lname.equals("read")) {
            Scanner sc = new Scanner(System.in);
            for (Object a : args) {
                // We expect variable names... but in expression form we already evaluated.
                // For simplicity just read and ignore assignment here (handled via assign stmt)
            }
            return null;
        }

        // User-defined procedure/function
        if (functions.containsKey(lname)) {
            return callUserFunction(functions.get(lname), args);
        }

        // Check if it's a method call on self
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            ClassDefinition.MethodInfo method = self.getClassDef().getMethod(name);
            if (method != null && method.body != null) {
                return callMethod(self, method, args);
            }
        }

        // Destructor call: obj.Free
        if (lname.equals("free")) {
            return null; // no-op in our interpreter
        }

        System.err.println("Warning: unknown procedure '" + name + "'");
        return null;
    }

    private String objectToString(Object o) {
        if (o == null) return "nil";
        if (o instanceof Boolean) return ((Boolean) o) ? "TRUE" : "FALSE";
        if (o instanceof Double) {
            Double d = (Double) o;
            if (d == Math.floor(d)) return String.valueOf(d.intValue());
            return String.valueOf(d);
        }
        return o.toString();
    }

    // ================================================================
    // FUNCTION / METHOD CALLING
    // ================================================================

    private Object callUserFunction(delphiParser.ProcedureOrFunctionDeclarationContext ctx, List<Object> args) {
        Environment saved = currentEnv;
        Environment fnEnv = new Environment(globalEnv);
        currentEnv = fnEnv;

        try {
            if (ctx.procedureDeclaration() != null) {
                delphiParser.ProcedureDeclarationContext pd = ctx.procedureDeclaration();
                bindParams(pd.formalParameterList(), args, fnEnv);
                visit(pd.block());
                return null;
            } else if (ctx.functionDeclaration() != null) {
                delphiParser.FunctionDeclarationContext fd = ctx.functionDeclaration();
                String funcName = fd.identifier().getText();
                fnEnv.define(funcName, null);  // result variable
                fnEnv.define("result", null);
                bindParams(fd.formalParameterList(), args, fnEnv);
                try {
                    visit(fd.block());
                } catch (ReturnException re) {
                    return re.getValue();
                }
                Object result = fnEnv.get("result");
                if (result == null) result = fnEnv.get(funcName);
                return result;
            }
        } finally {
            currentEnv = saved;
        }
        return null;
    }

    private Object callMethod(DelphiObject self, ClassDefinition.MethodInfo method, List<Object> args) {
        Environment saved = currentEnv;
        Environment methodEnv = new Environment(globalEnv);
        currentEnv = methodEnv;
        methodEnv.define("__self__", self);
        methodEnv.define("self", self);

        try {
            bindParamInfos(method.params, args, methodEnv);
            if (method.returnType != null) {
                methodEnv.define("result", null);
                methodEnv.define(method.name.toLowerCase(), null);
            }
            if (method.body != null) {
                try {
                    visitBlock((delphiParser.BlockContext) method.body);
                } catch (ReturnException re) {
                    return re.getValue();
                }
            }
            if (method.returnType != null) {
                Object res = methodEnv.get("result");
                return res;
            }
        } finally {
            currentEnv = saved;
        }
        return null;
    }

    private void bindParams(delphiParser.FormalParameterListContext ctx, List<Object> args, Environment env) {
        if (ctx == null) return;
        List<ClassDefinition.ParameterInfo> params = parseParamList(ctx);
        bindParamInfos(params, args, env);
    }

    private void bindParamInfos(List<ClassDefinition.ParameterInfo> params, List<Object> args, Environment env) {
        int i = 0;
        for (ClassDefinition.ParameterInfo p : params) {
            Object val = (i < args.size()) ? args.get(i) : null;
            env.define(p.name, val);
            i++;
        }
    }

    private List<ClassDefinition.ParameterInfo> parseParamList(delphiParser.FormalParameterListContext ctx) {
        List<ClassDefinition.ParameterInfo> params = new ArrayList<>();
        if (ctx == null) return params;
        for (delphiParser.FormalParameterSectionContext fps : ctx.formalParameterSection()) {
            boolean isVar = fps.VAR() != null;
            delphiParser.ParameterGroupContext pg = fps.parameterGroup();
            if (pg == null) continue;
            String typeName = pg.typeIdentifier().getText();
            for (delphiParser.IdentifierContext id : pg.identifierList().identifier()) {
                params.add(new ClassDefinition.ParameterInfo(id.getText(), typeName, isVar));
            }
        }
        return params;
    }

    // ================================================================
    // STRUCTURED STATEMENTS
    // ================================================================

    @Override
    public Object visitStructuredStatement(delphiParser.StructuredStatementContext ctx) {
        if (ctx.compoundStatement() != null) return visit(ctx.compoundStatement());
        if (ctx.conditionalStatement() != null) return visit(ctx.conditionalStatement());
        if (ctx.repetetiveStatement() != null) return visit(ctx.repetetiveStatement());
        if (ctx.withStatement() != null) return visit(ctx.withStatement());
        if (ctx.tryStatement() != null) return visit(ctx.tryStatement());
        return null;
    }

    @Override
    public Object visitIfStatement(delphiParser.IfStatementContext ctx) {
        Object cond = visit(ctx.expression());
        if (isTruthy(cond)) {
            visit(ctx.statement(0));
        } else if (ctx.ELSE() != null) {
            visit(ctx.statement(1));
        }
        return null;
    }

    @Override
    public Object visitWhileStatement(delphiParser.WhileStatementContext ctx) {
        while (isTruthy(visit(ctx.expression()))) {
            visit(ctx.statement());
        }
        return null;
    }

    @Override
    public Object visitRepeatStatement(delphiParser.RepeatStatementContext ctx) {
        do {
            visit(ctx.statements());
        } while (!isTruthy(visit(ctx.expression())));
        return null;
    }

    @Override
    public Object visitForStatement(delphiParser.ForStatementContext ctx) {
        String varName = ctx.identifier().getText();
        int start = toInt(visit(ctx.forList().initialValue().expression()));
        int end = toInt(visit(ctx.forList().finalValue().expression()));
        boolean downto = ctx.forList().DOWNTO() != null;

        if (!downto) {
            for (int i = start; i <= end; i++) {
                currentEnv.assign(varName, i);
                visit(ctx.statement());
            }
        } else {
            for (int i = start; i >= end; i--) {
                currentEnv.assign(varName, i);
                visit(ctx.statement());
            }
        }
        return null;
    }

    @Override
    public Object visitCaseStatement(delphiParser.CaseStatementContext ctx) {
        Object val = visit(ctx.expression());
        for (delphiParser.CaseListElementContext elem : ctx.caseListElement()) {
            for (delphiParser.ConstantContext c : elem.constList().constant()) {
                Object constVal = visit(c);
                if (equal(val, constVal)) {
                    visit(elem.statement());
                    return null;
                }
            }
        }
        // ELSE branch
        if (ctx.statements() != null) visit(ctx.statements());
        return null;
    }

    @Override
    public Object visitTryStatement(delphiParser.TryStatementContext ctx) {
        try {
            visit(ctx.statements());
        } catch (Exception e) {
            if (ctx.exceptBlock() != null) {
                visit(ctx.exceptBlock());
            }
        } finally {
            if (ctx.finallyBlock() != null) {
                visit(ctx.finallyBlock());
            }
        }
        return null;
    }

    @Override
    public Object visitWithStatement(delphiParser.WithStatementContext ctx) {
        // Execute statement in context of record variable
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
        String op = ctx.relationalOperator().getText();
        switch (op) {
            case "=":  return equal(left, right);
            case "<>": return !equal(left, right);
            case "<":  return compare(left, right) < 0;
            case "<=": return compare(left, right) <= 0;
            case ">":  return compare(left, right) > 0;
            case ">=": return compare(left, right) >= 0;
            case "is": return isInstance(left, right);
            default:   return false;
        }
    }

    @Override
    public Object visitSimpleExpression(delphiParser.SimpleExpressionContext ctx) {
        Object result = visit(ctx.term(0));
        if (ctx.sign() != null && ctx.sign().MINUS() != null) {
            result = negate(result);
        }
        for (int i = 0; i < ctx.additiveOperator().size(); i++) {
            Object right = visit(ctx.term(i + 1));
            String op = ctx.additiveOperator(i).getText().toLowerCase();
            switch (op) {
                case "+": result = add(result, right); break;
                case "-": result = subtract(result, right); break;
                case "or": result = (Boolean) toBoolean(result) || (Boolean) toBoolean(right); break;
                case "xor": result = (Boolean) toBoolean(result) ^ (Boolean) toBoolean(right); break;
            }
        }
        return result;
    }

    @Override
    public Object visitTerm(delphiParser.TermContext ctx) {
        Object result = visit(ctx.signedFactor(0));
        for (int i = 0; i < ctx.multiplicativeOperator().size(); i++) {
            Object right = visit(ctx.signedFactor(i + 1));
            String op = ctx.multiplicativeOperator(i).getText().toLowerCase();
            switch (op) {
                case "*": result = multiply(result, right); break;
                case "/": result = divide(result, right); break;
                case "div": result = intDivide(result, right); break;
                case "mod": result = mod(result, right); break;
                case "and": result = (Boolean) toBoolean(result) && (Boolean) toBoolean(right); break;
                case "shl": result = toInt(result) << toInt(right); break;
                case "shr": result = toInt(result) >> toInt(right); break;
            }
        }
        return result;
    }

    @Override
    public Object visitSignedFactor(delphiParser.SignedFactorContext ctx) {
        Object val = visit(ctx.factor());
        if (ctx.sign() != null && ctx.sign().MINUS() != null) {
            return negate(val);
        }
        return val;
    }

    @Override
    public Object visitFactor(delphiParser.FactorContext ctx) {
        if (ctx.variable() != null) return visitVariableExpression(ctx.variable());
        if (ctx.LPAREN() != null) return visit(ctx.expression());
        if (ctx.functionDesignator() != null) return visit(ctx.functionDesignator());
        if (ctx.unsignedConstant() != null) return visit(ctx.unsignedConstant());
        if (ctx.NOT() != null) return !isTruthy(visit(ctx.factor()));
        if (ctx.bool_() != null) return visit(ctx.bool_());
        if (ctx.NIL() != null) return null;
        if (ctx.objectCreation() != null) return visit(ctx.objectCreation());
        return null;
    }

    /** Variable access — resolves field chains like obj.field or self.field */
    private Object visitVariableExpression(delphiParser.VariableContext ctx) {
        List<delphiParser.IdentifierContext> ids = ctx.identifier();
        if (ids.isEmpty()) return null;

        String first = ids.get(0).getText();

        // Single identifier
        if (ids.size() == 1 && ctx.getChildCount() == 1) {
            // Check self fields first
            if (currentEnv.has("__self__")) {
                DelphiObject self = (DelphiObject) currentEnv.get("__self__");
                if (self.hasField(first)) return self.getField(first);
            }
            if (currentEnv.has(first)) return currentEnv.get(first);
            // Maybe it's a class name for static access
            return null;
        }

        // Multi-part: resolve step by step
        Object base;
        if (first.equalsIgnoreCase("self") && currentEnv.has("__self__")) {
            base = currentEnv.get("__self__");
        } else if (currentEnv.has(first)) {
            base = currentEnv.get(first);
        } else {
            return null;
        }

        for (int i = 1; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            String childText = child.getText();
            if (childText.equals(".")) {
                i++;
                if (i < ctx.getChildCount()) {
                    String field = ctx.getChild(i).getText();
                    if (base instanceof DelphiObject) {
                        DelphiObject obj = (DelphiObject) base;
                        // Check if it's a method call or field
                        if (obj.hasField(field)) {
                            base = obj.getField(field);
                        } else {
                            // Could be a method — will be called in functionDesignator
                            // For now return the object and method name pair
                            return new MethodCallRef(obj, field);
                        }
                    }
                }
            }
        }
        return base;
    }

    /** Temporary holder for obj.method reference before call */
    private static class MethodCallRef {
        final DelphiObject obj;
        final String methodName;
        MethodCallRef(DelphiObject obj, String methodName) {
            this.obj = obj;
            this.methodName = methodName;
        }
    }

    @Override
    public Object visitFunctionDesignator(delphiParser.FunctionDesignatorContext ctx) {
        String name = ctx.identifier().getText();
        List<Object> args = new ArrayList<>();
        if (ctx.parameterList() != null) {
            for (delphiParser.ActualParameterContext ap : ctx.parameterList().actualParameter()) {
                args.add(visit(ap.expression(0)));
            }
        }
        return callFunction(name, args);
    }

    private Object callFunction(String name, List<Object> args) {
        String lname = name.toLowerCase();

        // Built-in functions
        switch (lname) {
            case "writeln":
            case "write":
                return callBuiltinOrUserProc(name, args);
            case "readln": {
                Scanner sc = new Scanner(System.in);
                return sc.nextLine();
            }
            case "read": {
                Scanner sc = new Scanner(System.in);
                return sc.nextInt();
            }
            case "inttostr":
                return args.isEmpty() ? "" : String.valueOf(toInt(args.get(0)));
            case "strtoint":
                return args.isEmpty() ? 0 : Integer.parseInt(args.get(0).toString().trim());
            case "length":
                return args.isEmpty() ? 0 : args.get(0).toString().length();
            case "copy":
                if (args.size() >= 3) {
                    String s = args.get(0).toString();
                    int from = toInt(args.get(1)) - 1; // Pascal 1-based
                    int len = toInt(args.get(2));
                    int to = Math.min(from + len, s.length());
                    return s.substring(Math.max(0, from), to);
                }
                return "";
            case "pos":
                if (args.size() >= 2) {
                    int idx = args.get(1).toString().indexOf(args.get(0).toString());
                    return idx < 0 ? 0 : idx + 1;
                }
                return 0;
            case "upcase":
                return args.isEmpty() ? "" : args.get(0).toString().toUpperCase();
            case "lowercase":
                return args.isEmpty() ? "" : args.get(0).toString().toLowerCase();
            case "abs":
                if (!args.isEmpty()) {
                    Object v = args.get(0);
                    if (v instanceof Double) return Math.abs((Double) v);
                    return Math.abs(toInt(v));
                }
                return 0;
            case "sqr":
                if (!args.isEmpty()) {
                    double v = toDouble(args.get(0));
                    return v * v;
                }
                return 0.0;
            case "sqrt":
                return args.isEmpty() ? 0.0 : Math.sqrt(toDouble(args.get(0)));
            case "trunc":
                return args.isEmpty() ? 0 : (int) toDouble(args.get(0));
            case "round":
                return args.isEmpty() ? 0 : (int) Math.round(toDouble(args.get(0)));
            case "odd":
                return !args.isEmpty() && toInt(args.get(0)) % 2 != 0;
            case "succ":
                return args.isEmpty() ? 0 : toInt(args.get(0)) + 1;
            case "pred":
                return args.isEmpty() ? 0 : toInt(args.get(0)) - 1;
            case "ord":
                if (!args.isEmpty()) {
                    Object v = args.get(0);
                    if (v instanceof Character) return (int) (Character) v;
                    if (v instanceof String && !((String) v).isEmpty()) return (int) ((String) v).charAt(0);
                }
                return 0;
            case "chr":
                return args.isEmpty() ? '\0' : (char) toInt(args.get(0));
            case "str":
                return args.isEmpty() ? "" : String.valueOf(args.get(0));
            case "high":
                return Integer.MAX_VALUE;
            case "low":
                return Integer.MIN_VALUE;
        }

        // User-defined function
        if (functions.containsKey(lname)) {
            return callUserFunction(functions.get(lname), args);
        }

        // Method on self
        if (currentEnv.has("__self__")) {
            DelphiObject self = (DelphiObject) currentEnv.get("__self__");
            ClassDefinition.MethodInfo method = self.getClassDef().getMethod(name);
            if (method != null && method.body != null) {
                return callMethod(self, method, args);
            }
        }

        System.err.println("Warning: unknown function '" + name + "'");
        return null;
    }

    // ================================================================
    // OBJECT CREATION
    // ================================================================

    @Override
    public Object visitObjectCreation(delphiParser.ObjectCreationContext ctx) {
        String className;
        List<Object> args = new ArrayList<>();

        if (ctx.NEW() != null) {
            className = ctx.identifier().getText();
        } else {
            // ClassName.Create(...)
            className = ctx.identifier().getText();
        }

        if (ctx.parameterList() != null) {
            for (delphiParser.ActualParameterContext ap : ctx.parameterList().actualParameter()) {
                args.add(visit(ap.expression(0)));
            }
        }

        return instantiateClass(className, args);
    }

    private Object instantiateClass(String className, List<Object> args) {
        ClassDefinition classDef = classes.get(className.toLowerCase());
        if (classDef == null) {
            throw new RuntimeException("Unknown class: " + className);
        }
        DelphiObject obj = new DelphiObject(classDef);
        // Call constructor if present
        ClassDefinition.MethodInfo ctor = classDef.getConstructor();
        if (ctor != null && ctor.body != null) {
            callMethod(obj, ctor, args);
        }
        return obj;
    }

    // ================================================================
    // COMPLEX VARIABLE / METHOD ACCESS IN PROCEDURE STATEMENTS
    // ================================================================

    /**
     * Override visitProcedureStatement to handle method calls like obj.Method(args)
     * This is a simplistic approach — we check for dot-access in the call.
     */
    // We override visitStatement to intercept method calls expressed as statements
    // The grammar already handles this via variable (dot-chain) in assignmentStatement
    // and via procedureStatement for top-level calls.
    // For method calls like obj.DoSomething(), they appear as procedureStatement
    // but with a plain identifier. We'll detect them via the variable form.

    // ================================================================
    // EXTENDED CALL HANDLING FOR obj.Method(...)
    // ================================================================

    // We need to handle compound statements like: person.SetAge(25);
    // These are parsed differently. Let's override visitProcedureStatement to
    // detect dot-chains stored in the variable rule.

    // Actually in our grammar, "procedureStatement" only has an identifier.
    // Calls like obj.Method() would need to be parsed differently.
    // We'll handle it by checking assignmentStatement for the := operator,
    // and for method call statements we parse variable DOT identifier.

    // For this implementation, let's handle it by intercepting at statement level.
    // The grammar as written may not capture obj.Method() as a procedureStatement.
    // We'll add a workaround in visitVariable for MethodCallRef.

    // ================================================================
    // LITERALS / CONSTANTS
    // ================================================================

    @Override
    public Object visitUnsignedConstant(delphiParser.UnsignedConstantContext ctx) {
        if (ctx.unsignedNumber() != null) return visit(ctx.unsignedNumber());
        if (ctx.string() != null) return visit(ctx.string());
        if (ctx.NIL() != null) return null;
        return null;
    }

    @Override
    public Object visitUnsignedNumber(delphiParser.UnsignedNumberContext ctx) {
        if (ctx.unsignedInteger() != null) return visit(ctx.unsignedInteger());
        if (ctx.unsignedReal() != null) return visit(ctx.unsignedReal());
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
        // Strip surrounding quotes and unescape ''
        raw = raw.substring(1, raw.length() - 1);
        raw = raw.replace("''", "'");
        return raw;
    }

    @Override
    public Object visitBool_(delphiParser.Bool_Context ctx) {
        return ctx.TRUE() != null;
    }

    // ================================================================
    // ARITHMETIC HELPERS
    // ================================================================

    private Object add(Object a, Object b) {
        if (a instanceof String || b instanceof String) {
            return objectToString(a) + objectToString(b);
        }
        if (a instanceof Double || b instanceof Double) {
            return toDouble(a) + toDouble(b);
        }
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

    private Object divide(Object a, Object b) {
        return toDouble(a) / toDouble(b);
    }

    private Object intDivide(Object a, Object b) {
        return toInt(a) / toInt(b);
    }

    private Object mod(Object a, Object b) {
        return toInt(a) % toInt(b);
    }

    private Object negate(Object a) {
        if (a instanceof Double) return -(Double) a;
        return -toInt(a);
    }

    private boolean equal(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return toDouble(a) == toDouble(b);
        }
        return a.equals(b);
    }

    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(toDouble(a), toDouble(b));
        }
        return a.toString().compareTo(b.toString());
    }

    private boolean isInstance(Object obj, Object typeObj) {
        if (obj instanceof DelphiObject && typeObj instanceof String) {
            return ((DelphiObject) obj).getClassDef().getName().equalsIgnoreCase((String) typeObj);
        }
        return false;
    }

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Integer) return (Integer) val != 0;
        if (val instanceof Double) return (Double) val != 0.0;
        if (val instanceof String) return !((String) val).isEmpty();
        return true;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Double) return ((Double) val).intValue();
        if (val instanceof Boolean) return (Boolean) val ? 1 : 0;
        if (val instanceof String) {
            try { return Integer.parseInt(((String) val).trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Double) return (Double) val;
        if (val instanceof Integer) return ((Integer) val).doubleValue();
        if (val instanceof Boolean) return (Boolean) val ? 1.0 : 0.0;
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private Object toBoolean(Object val) {
        return isTruthy(val);
    }
}
