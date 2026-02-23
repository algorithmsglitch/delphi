grammar delphi;

// ============================================================
// PARSER RULES
// ============================================================

program
    : programHeading (usesClause)? block DOT EOF
    ;

programHeading
    : PROGRAM identifier (LPAREN identifierList RPAREN)? SEMI
    ;

usesClause
    : USES identifierList SEMI
    ;

block
    : (declarationPart)* compoundStatement
    ;

declarationPart
    : labelDeclaration
    | constantDefinitionPart
    | typeDefinitionPart
    | variableDeclarationPart
    | procedureAndFunctionDeclarationPart
    ;

// ============================================================
// CLASS BODY
// ============================================================

classType
    : CLASS (LPAREN identifierList RPAREN)? classBody END
    | CLASS (LPAREN identifierList RPAREN)?   // forward declaration
    ;

classBody
    : (classSection)*
    ;

classSection
    : (visibilitySpecifier)? classMemberList
    ;

visibilitySpecifier
    : PRIVATE
    | PROTECTED
    | PUBLIC
    | PUBLISHED
    ;

classMemberList
    : classMember+
    ;

classMember
    : fieldDeclaration
    | methodDeclaration
    | constructorDeclaration
    | destructorDeclaration
    ;

fieldDeclaration
    : identifierList COLON typeIdentifier SEMI
    ;

methodDeclaration
    : (PROCEDURE | FUNCTION) identifier (LPAREN formalParameterList RPAREN)? (COLON typeIdentifier)? SEMI (methodDirective SEMI)*
    ;

methodDirective
    : VIRTUAL
    | OVERRIDE
    | ABSTRACT
    | OVERRIDE
    ;

constructorDeclaration
    : CONSTRUCTOR identifier (LPAREN formalParameterList RPAREN)? SEMI (methodDirective SEMI)*
    ;

destructorDeclaration
    : DESTRUCTOR identifier (LPAREN formalParameterList RPAREN)? SEMI (methodDirective SEMI)*
    ;

// ============================================================
// LABEL / CONSTANT / TYPE / VARIABLE DECLARATIONS
// ============================================================

labelDeclaration
    : LABEL label (COMMA label)* SEMI
    ;

label
    : unsignedInteger
    | identifier
    ;

constantDefinitionPart
    : CONST (constantDefinition SEMI)+
    ;

constantDefinition
    : identifier EQUALS constant
    ;

constant
    : unsignedNumber
    | sign unsignedNumber
    | identifier
    | sign identifier
    | string
    | constantChr
    ;

constantChr
    : CHR LPAREN unsignedInteger RPAREN
    ;

typeDefinitionPart
    : TYPE (typeDefinition SEMI)+
    ;

typeDefinition
    : identifier EQUALS (type_ | classType | interfaceType)
    ;

interfaceType
    : INTERFACE (LPAREN identifierList RPAREN)? interfaceBody END
    ;

interfaceBody
    : interfaceMember*
    ;

interfaceMember
    : methodDeclaration
    ;

type_
    : simpleType
    | structuredType
    | pointerType
    | typeIdentifier
    ;

simpleType
    : subrangeType
    | enumeratedType
    | typeIdentifier
    ;

subrangeType
    : constant DOTDOT constant
    ;

enumeratedType
    : LPAREN identifierList RPAREN
    ;

structuredType
    : PACKED? unpackedStructuredType
    ;

unpackedStructuredType
    : arrayType
    | setType
    | fileType
    | recordType
    ;

arrayType
    : ARRAY LBRACKET typeList RBRACKET OF type_
    ;

typeList
    : indexType (COMMA indexType)*
    ;

indexType
    : simpleType
    ;

setType
    : SET OF baseType
    ;

baseType
    : simpleType
    ;

fileType
    : FILE OF type_
    | FILE
    ;

recordType
    : RECORD fieldList? END
    ;

fieldList
    : fixedPart (SEMI variantPart)?
    | variantPart
    ;

fixedPart
    : recordSection (SEMI recordSection)*
    ;

recordSection
    : identifierList COLON type_
    ;

variantPart
    : CASE tag OF variant (SEMI variant)*
    ;

tag
    : identifier COLON typeIdentifier
    | typeIdentifier
    ;

variant
    : constList COLON LPAREN fieldList? RPAREN
    ;

pointerType
    : POINTER typeIdentifier
    ;

variableDeclarationPart
    : VAR (variableDeclaration SEMI)+
    ;

variableDeclaration
    : identifierList COLON type_
    ;

procedureAndFunctionDeclarationPart
    : procedureOrFunctionDeclaration SEMI
    ;

procedureOrFunctionDeclaration
    : procedureDeclaration
    | functionDeclaration
    | constructorImplementation
    | destructorImplementation
    | methodImplementation
    ;

procedureDeclaration
    : PROCEDURE identifier (LPAREN formalParameterList RPAREN)? SEMI block
    ;

functionDeclaration
    : FUNCTION identifier (LPAREN formalParameterList RPAREN)? COLON typeIdentifier SEMI block
    ;

constructorImplementation
    : CONSTRUCTOR identifier DOT identifier (LPAREN formalParameterList RPAREN)? SEMI block
    ;

destructorImplementation
    : DESTRUCTOR identifier DOT identifier (LPAREN formalParameterList RPAREN)? SEMI block
    ;

methodImplementation
    : (PROCEDURE | FUNCTION) identifier DOT identifier (LPAREN formalParameterList RPAREN)? (COLON typeIdentifier)? SEMI block
    ;

formalParameterList
    : LPAREN formalParameterSection (SEMI formalParameterSection)* RPAREN
    | formalParameterSection (SEMI formalParameterSection)*
    ;

formalParameterSection
    : parameterGroup
    | VAR parameterGroup
    | FUNCTION parameterGroup
    | PROCEDURE parameterGroup
    ;

parameterGroup
    : identifierList COLON typeIdentifier
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

constList
    : constant (COMMA constant)*
    ;

// ============================================================
// STATEMENTS
// ============================================================

compoundStatement
    : BEGIN statements END
    ;

statements
    : statement (SEMI statement)*
    ;

statement
    : label COLON statement
    | simpleStatement
    | structuredStatement
    |
    ;

simpleStatement
    : assignmentStatement
    | procedureStatement
    | gotoStatement
    | emptyStatement_
    ;

assignmentStatement
    : variable ASSIGN expression
    ;

variable
    : (AT identifier | identifier) (LBRACKET expression (COMMA expression)* RBRACKET | DOT identifier | POINTER)*
    ;

procedureStatement
    : identifier (LPAREN parameterList RPAREN)?
    ;

gotoStatement
    : GOTO label
    ;

emptyStatement_
    :
    ;

structuredStatement
    : compoundStatement
    | conditionalStatement
    | repetetiveStatement
    | withStatement
    | tryStatement
    ;

conditionalStatement
    : ifStatement
    | caseStatement
    ;

ifStatement
    : IF expression THEN statement (ELSE statement)?
    ;

caseStatement
    : CASE expression OF caseListElement (SEMI caseListElement)* (SEMI)? (ELSE statements)? END
    ;

caseListElement
    : constList COLON statement
    ;

repetetiveStatement
    : whileStatement
    | repeatStatement
    | forStatement
    ;

whileStatement
    : WHILE expression DO statement
    ;

repeatStatement
    : REPEAT statements UNTIL expression
    ;

forStatement
    : FOR identifier ASSIGN forList DO statement
    ;

forList
    : initialValue (TO | DOWNTO) finalValue
    ;

initialValue
    : expression
    ;

finalValue
    : expression
    ;

withStatement
    : WITH recordVariableList DO statement
    ;

recordVariableList
    : variable (COMMA variable)*
    ;

tryStatement
    : TRY statements (exceptBlock | finallyBlock) END
    ;

exceptBlock
    : EXCEPT statements
    ;

finallyBlock
    : FINALLY statements
    ;

// ============================================================
// EXPRESSIONS
// ============================================================

parameterList
    : actualParameter (COMMA actualParameter)*
    ;

actualParameter
    : expression (COLON expression (COLON expression)?)?
    ;

expression
    : simpleExpression (relationalOperator simpleExpression)?
    ;

relationalOperator
    : EQUALS
    | NOT_EQUALS
    | LT
    | LE
    | GE
    | GT
    | IN
    | IS
    ;

simpleExpression
    : (sign)? term (additiveOperator term)*
    ;

additiveOperator
    : PLUS
    | MINUS
    | OR
    | XOR
    ;

term
    : signedFactor (multiplicativeOperator signedFactor)*
    ;

multiplicativeOperator
    : STAR
    | SLASH
    | DIV
    | MOD
    | AND
    | SHL
    | SHR
    ;

signedFactor
    : (sign)? factor
    ;

factor
    : variable
    | LPAREN expression RPAREN
    | functionDesignator
    | unsignedConstant
    | set_
    | NOT factor
    | bool_
    | NIL
    | objectCreation
    ;

objectCreation
    : identifier DOT CREATE (LPAREN parameterList RPAREN)?
    | NEW identifier (LPAREN parameterList RPAREN)?
    ;

bool_
    : TRUE
    | FALSE
    ;

functionDesignator
    : identifier LPAREN parameterList RPAREN
    ;

unsignedConstant
    : unsignedNumber
    | string
    | NIL
    ;

set_
    : LBRACKET (element (COMMA element)*)? RBRACKET
    ;

element
    : expression (DOTDOT expression)?
    ;

typeIdentifier
    : identifier
    | CHAR
    | BOOLEAN
    | INTEGER
    | REAL
    | STRING
    ;

unsignedNumber
    : unsignedInteger
    | unsignedReal
    ;

unsignedInteger
    : NUM_INT
    ;

unsignedReal
    : NUM_REAL
    ;

sign
    : PLUS
    | MINUS
    ;

bool
    : TRUE
    | FALSE
    ;

string
    : STRING_LITERAL
    ;

identifier
    : IDENT
    | RESULT
    | SELF
    ;

// ============================================================
// LEXER RULES
// ============================================================

// Keywords
AND         : [Aa][Nn][Dd];
ARRAY       : [Aa][Rr][Rr][Aa][Yy];
ABSTRACT    : [Aa][Bb][Ss][Tt][Rr][Aa][Cc][Tt];
AS          : [Aa][Ss];
AT          : '@';
BEGIN       : [Bb][Ee][Gg][Ii][Nn];
BOOLEAN     : [Bb][Oo][Oo][Ll][Ee][Aa][Nn];
CASE        : [Cc][Aa][Ss][Ee];
CHAR        : [Cc][Hh][Aa][Rr];
CHR         : [Cc][Hh][Rr];
CLASS       : [Cc][Ll][Aa][Ss][Ss];
CONST       : [Cc][Oo][Nn][Ss][Tt];
CONSTRUCTOR : [Cc][Oo][Nn][Ss][Tt][Rr][Uu][Cc][Tt][Oo][Rr];
CREATE      : [Cc][Rr][Ee][Aa][Tt][Ee];
DESTRUCTOR  : [Dd][Ee][Ss][Tt][Rr][Uu][Cc][Tt][Oo][Rr];
DIV         : [Dd][Ii][Vv];
DO          : [Dd][Oo];
DOWNTO      : [Dd][Oo][Ww][Nn][Tt][Oo];
ELSE        : [Ee][Ll][Ss][Ee];
END         : [Ee][Nn][Dd];
EXCEPT      : [Ee][Xx][Cc][Ee][Pp][Tt];
FALSE       : [Ff][Aa][Ll][Ss][Ee];
FILE        : [Ff][Ii][Ll][Ee];
FINALLY     : [Ff][Ii][Nn][Aa][Ll][Ll][Yy];
FOR         : [Ff][Oo][Rr];
FREE        : [Ff][Rr][Ee][Ee];
FUNCTION    : [Ff][Uu][Nn][Cc][Tt][Ii][Oo][Nn];
GOTO        : [Gg][Oo][Tt][Oo];
IF          : [Ii][Ff];
IMPLEMENTATION : [Ii][Mm][Pp][Ll][Ee][Mm][Ee][Nn][Tt][Aa][Tt][Ii][Oo][Nn];
IN          : [Ii][Nn];
INHERITED   : [Ii][Nn][Hh][Ee][Rr][Ii][Tt][Ee][Dd];
INTEGER     : [Ii][Nn][Tt][Ee][Gg][Ee][Rr];
INTERFACE   : [Ii][Nn][Tt][Ee][Rr][Ff][Aa][Cc][Ee];
IS          : [Ii][Ss];
LABEL       : [Ll][Aa][Bb][Ee][Ll];
MOD         : [Mm][Oo][Dd];
NEW         : [Nn][Ee][Ww];
NIL         : [Nn][Ii][Ll];
NOT         : [Nn][Oo][Tt];
OF          : [Oo][Ff];
OR          : [Oo][Rr];
OVERRIDE    : [Oo][Vv][Ee][Rr][Rr][Ii][Dd][Ee];
PACKED      : [Pp][Aa][Cc][Kk][Ee][Dd];
POINTER     : '^';
PRIVATE     : [Pp][Rr][Ii][Vv][Aa][Tt][Ee];
PROCEDURE   : [Pp][Rr][Oo][Cc][Ee][Dd][Uu][Rr][Ee];
PROGRAM     : [Pp][Rr][Oo][Gg][Rr][Aa][Mm];
PROTECTED   : [Pp][Rr][Oo][Tt][Ee][Cc][Tt][Ee][Dd];
PUBLIC      : [Pp][Uu][Bb][Ll][Ii][Cc];
PUBLISHED   : [Pp][Uu][Bb][Ll][Ii][Ss][Hh][Ee][Dd];
REAL        : [Rr][Ee][Aa][Ll];
RECORD      : [Rr][Ee][Cc][Oo][Rr][Dd];
REPEAT      : [Rr][Ee][Pp][Ee][Aa][Tt];
RESULT      : [Rr][Ee][Ss][Uu][Ll][Tt];
SELF        : [Ss][Ee][Ll][Ff];
SET         : [Ss][Ee][Tt];
SHL         : [Ss][Hh][Ll];
SHR         : [Ss][Hh][Rr];
STRING      : [Ss][Tt][Rr][Ii][Nn][Gg];
THEN        : [Tt][Hh][Ee][Nn];
TO          : [Tt][Oo];
TRUE        : [Tt][Rr][Uu][Ee];
TRY         : [Tt][Rr][Yy];
TYPE        : [Tt][Yy][Pp][Ee];
UNIT        : [Uu][Nn][Ii][Tt];
UNTIL       : [Uu][Nn][Tt][Ii][Ll];
USES        : [Uu][Ss][Ee][Ss];
VAR         : [Vv][Aa][Rr];
VIRTUAL     : [Vv][Ii][Rr][Tt][Uu][Aa][Ll];
WHILE       : [Ww][Hh][Ii][Ll][Ee];
WITH        : [Ww][Ii][Tt][Hh];
WRITELN     : [Ww][Rr][Ii][Tt][Ee][Ll][Nn];
WRITE       : [Ww][Rr][Ii][Tt][Ee];
XOR         : [Xx][Oo][Rr];

// Symbols
ASSIGN      : ':=';
PLUS        : '+';
MINUS       : '-';
STAR        : '*';
SLASH       : '/';
EQUALS      : '=';
NOT_EQUALS  : '<>';
LT          : '<';
LE          : '<=';
GE          : '>=';
GT          : '>';
LPAREN      : '(';
RPAREN      : ')';
LBRACKET    : '[';
RBRACKET    : ']';
COLON       : ':';
SEMI        : ';';
COMMA       : ',';
DOT         : '.';
DOTDOT      : '..';

// Literals
NUM_INT     : [0-9]+;
NUM_REAL    : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)?;
STRING_LITERAL : '\'' (~'\'' | '\'\'')* '\'';

// Identifiers
IDENT       : [a-zA-Z_] [a-zA-Z0-9_]*;

// Whitespace / Comments
WS          : [ \t\r\n]+ -> skip;
COMMENT1    : '{' .*? '}' -> skip;
COMMENT2    : '(*' .*? '*)' -> skip;
COMMENT3    : '//' ~[\r\n]* -> skip;
