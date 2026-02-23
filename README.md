# Delphi Interpreter — CS Project

A Java interpreter for a Delphi/Pascal subset, built using ANTLR4.

---

## What Is Implemented

### Core Requirements
| Feature | Status |
|---|---|
| ANTLR4 Grammar (`delphi.g4`) | ✅ |
| Java Interpreter (tree-walking visitor) | ✅ |
| Classes & Objects | ✅ |
| Constructors | ✅ |
| Destructors | ✅ |
| Encapsulation (`private`, `public`, `protected`) | ✅ |
| Integer I/O (`WriteLn`, `Write`, `ReadLn`) | ✅ |
| Test cases | ✅ (5 test files) |

### Bonus Features
| Feature | Status |
|---|---|
| Inheritance (`class(ParentClass)`) | ✅ |
| Interfaces (`interface` keyword) | ✅ |
| Virtual / Override methods | ✅ |

---

## Project Structure

```
delphi/
├── grammar/
│   └── delphi.g4          ← ANTLR4 grammar (extends Pascal)
├── src/
│   └── main/java/delphi/
│       ├── DelphiInterpreter.java  ← Main entry point
│       ├── DelphiVisitor.java      ← AST interpreter
│       ├── ClassDefinition.java    ← Class metadata model
│       ├── DelphiObject.java       ← Runtime object instances
│       ├── Environment.java        ← Variable scope/symbol table
│       └── ReturnException.java    ← Control flow helper
├── tests/
│   ├── test1.pas   ← Basic classes, constructor, destructor, encapsulation
│   ├── test2.pas   ← Object composition (two interacting classes)
│   ├── test3.pas   ← Inheritance with virtual/override (BONUS)
│   ├── test4.pas   ← Interfaces (BONUS)
│   └── test5.pas   ← Terminal I/O with integer operations
└── pom.xml         ← Maven build file
```

---

## Prerequisites

- **Java 11+** — `java -version`
- **Maven 3.6+** — `mvn -version`

---

## How to Build

From the `delphi/` directory:

```bash
mvn clean package
```

This will:
1. Run the ANTLR4 Maven plugin to generate `delphiLexer.java`, `delphiParser.java`, and `delphiBaseVisitor.java` from `grammar/delphi.g4`
2. Compile all Java sources
3. Package a fat executable JAR at `target/delphi-interpreter.jar`

---

## How to Run a Test

```bash
java -jar target/delphi-interpreter.jar tests/test1.pas
java -jar target/delphi-interpreter.jar tests/test2.pas
java -jar target/delphi-interpreter.jar tests/test3.pas
java -jar target/delphi-interpreter.jar tests/test4.pas
java -jar target/delphi-interpreter.jar tests/test5.pas
```

---

## Running All Tests (bash)

```bash
for i in 1 2 3 4 5; do
  echo "=========================================="
  echo "  Running test${i}.pas"
  echo "=========================================="
  java -jar target/delphi-interpreter.jar tests/test${i}.pas
  echo ""
done
```

---

## Test Descriptions

### test1.pas — Encapsulation + Constructor + Destructor
Creates a `TPerson` class with `private` fields `FName` and `FAge`.
Demonstrates getter/setter methods enforcing data integrity (no negative age).
Shows constructor initialization and destructor teardown message.

### test2.pas — Object Composition
Two classes: `TPoint` and `TRectangle`.
`TRectangle` owns a `TPoint` instance as a field, demonstrating how objects reference other objects.
Computes area and perimeter.

### test3.pas — Inheritance *(BONUS)*
Hierarchy: `TAnimal` → `TDog` and `TAnimal` → `TCat`.
Demonstrates `virtual` and `override` methods (`Speak`, `PrintInfo`).
Child classes add their own fields (`FBreed`, `FIsIndoor`) and override base behavior.
Base class methods (`GetName`, `GetAge`) are inherited and usable on child instances.

### test4.pas — Interfaces *(BONUS)*
Declares `IShape` and `IPrintable` interfaces.
`TSquare` implements both; `TCircleApprox` implements `IShape`.
Shows that different classes can satisfy the same interface contract.

### test5.pas — Integer I/O with OOP
`TCounter` class enforces a bounded integer counter with min/max limits.
Uses `for` loops and method calls to exercise I/O of integer values with `WriteLn`.

---

## Grammar Notes

The grammar (`delphi.g4`) extends the standard Pascal grammar with the following Delphi-specific additions:

- **`classDeclaration`** — `TYPE Foo = CLASS ... END;`
- **`classBody`** / **`classSection`** — visibility sections (`PRIVATE`, `PUBLIC`, etc.)
- **`constructorDeclaration`** / **`destructorDeclaration`** — inside class body
- **`constructorImplementation`** / **`destructorImplementation`** — `CONSTRUCTOR ClassName.Create ...`
- **`methodImplementation`** — `PROCEDURE ClassName.MethodName ...`
- **`interfaceDeclaration`** — `TYPE IFoo = INTERFACE ... END;`
- **`objectCreation`** — `ClassName.Create(args)` or `NEW ClassName(args)`
- **`methodDirective`** — `VIRTUAL`, `OVERRIDE`, `ABSTRACT`
- **`visibilitySpecifier`** — `PRIVATE`, `PROTECTED`, `PUBLIC`, `PUBLISHED`

---

## Known Limitations

- No garbage collection (destructor messages are printed but memory is managed by the JVM)
- No real pointer arithmetic (`^` type exists in grammar but is not interpreted)
- Interface method dispatch is structural (matching by name), not vtable-based
- `ReadLn` for integer input returns a string; use `StrToInt` for conversion in numeric contexts
- `GOTO` statements are parsed but will throw a runtime error (as they are generally inadvisable)
