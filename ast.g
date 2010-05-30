grammar ceylon;

options {
    //backtrack=true;
    memoize=true;
    output=AST;
}

tokens {
    ANNOTATION;
    ANNOTATION_LIST;
    DECLARE;
    MEMBER_DECL;
    TYPE_DECL;
    ABSTRACT_MEMBER_DECL;
    ALIAS_DECL;
    ANNOTATION_NAME;
    ARG_LIST;
    ARG_NAME;
    ANON_METH;
    ATTRIBUTE_SETTER;
    BREAK_STMT;
    CALL_EXPR;
    CATCH_BLOCK;
    CATCH_STMT;
    CHAR_CST;
    CLASS_BODY;
    CLASS_DECL;
    CONDITION;
    DECL_MODIFIER;
    DO_BLOCK;
    DO_ITERATOR;
    DO_ITERATOR;
    EXPR;
    FINALLY_BLOCK;
    FORMAL_PARAMETER;
    IF_FALSE;
    IF_STMT;
    IF_TRUE;
    IMPORT_DECL;
    IMPORT_LIST;
    IMPORT_WILDCARD;
    IMPORT_PATH;
    INIT_EXPR;
    INST_DECL;
    INTERFACE_DECL;
    MEMBER_NAME;
    MEMBER_TYPE;
    METHOD_EXPR;
    NAMED_ARG;
    UNNAMED_ARG;
    NIL;
    RET_STMT;
    STMT_LIST;
    STRING_CST;
    THROW_STMT;
    RETRY_STMT;
    TRY_BLOCK;
    TRY_CATCH_STMT;
    TRY_RESOURCE_LIST;
    TRY_STMT;
    TYPE_ARG_LIST;
    TYPE_NAME;
    TYPE_PARAMETER_LIST;
    USER_ANNOTATION;
    VAR_DECL;
    WHILE_BLOCK;
    WHILE_STMT;
    SWITCH_STMT;
    SWITCH_EXPR;
    SWITCH_CASE_LIST;
    CASE_ITEM;
    EXPR_LIST;
    CASE_DEFAULT;
    TYPE_CONSTRAINT_LIST;
    TYPE;
    TYPE_CONSTRAINT;
    TYPE_DECL;
    SATISFIES_LIST;
    SUBSCRIPT_EXPR;
    LOWER_BOUND;
    UPPER_BOUND;
    SELECTOR_LIST;
    TYPE_VARIANCE;
    TYPE_PARAMETER;
    STRING_CONCAT;
    INT_CST;
    FLOAT_CST;
    QUOTE_CST;
    FOR_STMT;
    FOR_ITERATOR;
    FAIL_BLOCK;
    LOOP_BLOCK;
    FOR_CONTAINMENT;
    REFLECTED_LITERAL;
    ENUM_LIST;
    SUPERCLASS;
    PREFIX_EXPR;
    POSTFIX_EXPR;
    EXISTS_EXPR;
    NONEMPTY_EXPR;
    IS_EXPR;
    GET_EXPR;
    SET_EXPR;
}

compilationUnit
    : importDeclaration*
      (annotations? typeDeclaration)+
      EOF
      ->
      ^(IMPORT_LIST importDeclaration*)
      ^(TYPE_DECL annotations? typeDeclaration)+
     ;
       
typeDeclaration
    : classDeclaration -> ^(CLASS_DECL classDeclaration)
    | interfaceDeclaration -> ^(INTERFACE_DECL interfaceDeclaration)
    | aliasDeclaration -> ^(ALIAS_DECL aliasDeclaration)
    ;

importDeclaration  
    : 'import' importPath ('.' wildcard | alias)? ';'
    -> ^(IMPORT_DECL ^(TYPE_NAME importElement*) wildcard? alias?)
    ;
    
importPath
    : importElement ('.' importElement)*
    -> ^(IMPORT_PATH importElement*)
    ;
    
wildcard
    : '*'
    -> ^(IMPORT_WILDCARD)
    ;
    
alias
    : 'alias' typeName
    -> ^(ALIAS_DECL typeName)
    ;
    
importElement
    : LIDENTIFIER | UIDENTIFIER
    ;

block
    : '{' declarationOrStatement* directiveStatement? '}'
    -> ^(STMT_LIST declarationOrStatement* directiveStatement?)
    ;

/*inlineClassDeclaration
    : 'new' 
      annotations?
      type
      positionalArguments?
      satisfiedTypes?
      inlineClassBody
    ;

inlineClassBody
    : '{' declarationOrStatement* '}'
    ;*/

//We could eliminate the backtracking by requiring
//all member declarations to begin with a keyword
//Note that this accepts more then the language spec
//since it does not enforce that enumerated class
//instances have to be listed together at the top
//of the class body
declarationOrStatement
    : (declarationStart) => declaration | statement
    ;

//TODO: I don't understand why we need to distinguish
//      methods from attributes at this stage. Why not
//      do it later?
declaration
    :
    ann=annotations? 
    ( 
        membd=memberDeclaration -> ^(MEMBER_DECL $membd $ann?)
      | typed=typeDeclaration -> ^(TYPE_DECL $typed $ann?)
      | instd=instance -> ^(INST_DECL $instd $ann?)
    )
/*    (((memberHeader memberParameters) => 
            (mem=memberDeclaration 
                -> ^(METHOD_DECL $mem $ann?)))
    | (mem=memberDeclaration 
            -> ^(MEMBER_DECL $mem $ann?))
    | (typ=typeDeclaration 
            -> ^(TYPE_DECL $typ $ann?))
    | (inst=instance
            -> ^(INSTANCE $inst $ann?)))*/
    ;
    
//special rule for syntactic predicates
declarationStart
    :  declarationModifier 
    | ( userAnnotation annotations? )? ( memberDeclarationStart | typeDeclarationStart )
    ;

memberDeclarationStart
    : (type|'assign'|'void'|'case') LIDENTIFIER
    ;
    
typeDeclarationStart
    : ('class'|'interface'|'alias') UIDENTIFIER
    ;

//by making these things keywords, we reduce the amount of
//backtracking
declarationModifier 
    : modifier
    -> ^(DECL_MODIFIER modifier)
    ;

modifier
    : 'public'
    | 'module'
    | 'package'
    | 'private'
    | 'abstract'
    | 'default'
    | 'override'
    | 'optional'
    | 'mutable'
    | 'static'
    | 'once'
    | 'deprecated'
    | 'volatile'
    | 'extension'
    ;

statement 
    : expressionStatement
    | controlStructure
    ;

expressionStatement
    : expression ';'!
    ;

directiveStatement
    : directive ';'?
    -> directive
    ;

directive
    : 'return' assignable? -> ^(RET_STMT assignable?)
    | 'throw' expression? -> ^(THROW_STMT expression?)
    | 'break' expression? -> ^(BREAK_STMT expression?)
    | 'retry' -> ^(RETRY_STMT)
    ;

abstractMemberDeclaration
    : ann=annotations?
      head=memberHeader
      param=memberParameters?
      -> ^(ABSTRACT_MEMBER_DECL $head $param? $ann?)
/*    (((memberHeader memberParameters) => 
        (memberHeader memberParameters ';'
          -> ^(ABSTRACT_METHOD_DECL $ann? memberHeader memberParameters)))
    | (mem=memberDeclaration 
            -> ^(MEMBER_DECL $mem $ann?)))*/
    ;

memberDeclaration
    : memberHeader memberDefinition
    ;

memberHeader
    : memberType memberName -> ^(MEMBER_TYPE memberType) memberName
    | 'assign' memberName -> ^(ATTRIBUTE_SETTER memberName)
    ;

memberType
    : type | 'void'
    ;

memberParameters
    : typeParameters? formalParameters+ typeConstraints?
    ;

//TODO: should we allow the shortcut style of method
//      definition for a method or getter which returns
//      a parExpression, just like we do for smalltalk
//      style parameters below?
memberDefinition
options {backtrack = true;}
    : memberParameters? ';'
    | memberParameters? (block | '='! expression ';'!)
    | memberInitializer? ';'!
    ;

memberInitializer
    : specifier | initializer
    ;
    
interfaceDeclaration
    :
        'interface'!
        typeName
        typeParameters?
        satisfiedTypes?
        typeConstraints?
        interfaceBody
    ;

interfaceBody
    //TODO: this is not quite right, since type declarations
    //      can have annotations
    : '{'! (abstractMemberDeclaration | typeDeclaration)* '}'!
    ;

aliasDeclaration
    :
        'alias'!
        typeName
        typeParameters?
        satisfiedTypes?
        typeConstraints?
        ';'!
    ;

classDeclaration
    :
        'class'!
        typeName
        typeParameters?
        formalParameters?
        extendedType?
        satisfiedTypes?
        typeConstraints?
        classBody
    ;

classBody
    : '{' declarationOrStatement* '}'
    -> ^(STMT_LIST declarationOrStatement*)
 //    -> ^(CLASS_BODY ^(STMT_LIST $stmts))
    ;

extendedType
    : 'extends' type positionalArguments
    -> ^(SUPERCLASS type positionalArguments) 
    ;

instance
    : 'case'! memberName arguments? (','|';'|'...')
    // FIXME: Need to add ellipsis
    ;
    

typeConstraint
    : typeName ( ('>='^ type) | ('<='^ type) | ('='^ 'subtype') | formalParameters )
    ;
    
typeConstraints
    //TODO: should it be 'for'?
    : 'where' typeConstraint ('&' typeConstraint)*
    -> ^(TYPE_CONSTRAINT_LIST ^(TYPE_CONSTRAINT typeConstraint)+)
    ;
    
satisfiedTypes
    : 'satisfies' type (',' type)*
    -> ^(SATISFIES_LIST type+)
    ;

type
    : parameterizedType -> parameterizedType
    | 'subtype' -> ^(TYPE 'subtype')
    ;

parameterizedType
    : qualifiedTypeName typeArguments?
    -> ^(TYPE qualifiedTypeName typeArguments?)
    ;

annotations
    : annotation+
    -> ^(ANNOTATION_LIST annotation+)
    ;

annotation
    : declarationModifier | userAnnotation
    ;

//TODO: we could minimize backtracking by limiting the 
//kind of expressions that can appear as arguments to
//the annotation
userAnnotation 
    : annotationName annotationArguments?
    -> ^(USER_ANNOTATION annotationName annotationArguments?)
    ;

annotationArguments
    : arguments | ( literal | reflectedLiteral )+
    ;

reflectedLiteral 
    : '#' ( memberName | (type ( '.' memberName )? ) )
    -> ^(REFLECTED_LITERAL type? memberName?)
    ;

qualifiedTypeName
    : UIDENTIFIER ('.' UIDENTIFIER)*
    -> ^(TYPE_NAME UIDENTIFIER+) 
    ;

typeName
    : UIDENTIFIER
    -> ^(TYPE_NAME UIDENTIFIER)
    ;

annotationName
    : LIDENTIFIER
    -> ^(ANNOTATION_NAME LIDENTIFIER)
    ;

memberName 
    : LIDENTIFIER
    -> ^(MEMBER_NAME LIDENTIFIER)
    ;

typeArguments
    : '<' type (',' type)* '>'
    -> ^(TYPE_ARG_LIST type+)
    ;

typeParameters
    : '<' typeParameter (',' typeParameter)* '>'
    -> ^(TYPE_PARAMETER_LIST typeParameter+)
    ;

typeParameter
    : variance? typeName
    -> ^(TYPE_PARAMETER ^(TYPE_VARIANCE variance)? typeName)
    ;

variance
    : 'in' | 'out'
    ;
    
//for locals and attributes
initializer
    : ':=' assignable
    -> ^(INIT_EXPR assignable)
    ;

//for parameters
specifier
    : '=' assignable
    -> ^(INIT_EXPR assignable)
    ;

literal
    : NATURALLITERAL -> ^(INT_CST NATURALLITERAL)
    | FLOATLITERAL -> ^(FLOAT_CST FLOATLITERAL)
    | QUOTEDLITERAL -> ^(QUOTE_CST QUOTEDLITERAL)
    | CHARLITERAL -> ^(CHAR_CST CHARLITERAL)
    | SIMPLESTRINGLITERAL -> ^(STRING_CST SIMPLESTRINGLITERAL)
    | stringExpr -> ^(STRING_CONCAT stringExpr)
    ;   

stringExpr
    : leftStringLiteral innerStringExpr (middleStringLiteral innerStringExpr)* rightStringLiteral
    ;

innerStringExpr
    : expression
    ;

leftStringLiteral
    : LEFTSTRINGLITERAL -> ^(STRING_CST LEFTSTRINGLITERAL)
    ;
    
middleStringLiteral
    : MIDDLESTRINGLITERAL -> ^(STRING_CST MIDDLESTRINGLITERAL)
    ;
    
rightStringLiteral
    : RIGHTSTRINGLITERAL -> ^(STRING_CST RIGHTSTRINGLITERAL)
    ;
    
assignable 
    : reflectedLiteral
    | expression
    ;

expression
    : expr
    -> ^(EXPR expr)
    ;

//Even though it looks like this is non-associative
//assignment, it is actually right associative because
//assignable can be an assignment
//Note that = is not really an assignment operator, but 
//can be used to init locals
expr
    : implicationExpression
      (('='^ | ':='^ | '.='^ | '+='^ | '-='^ | '*='^ | '/='^ | '%='^ | '&='^ | '|='^ | '^='^ | '&&='^ | '||='^ | '?='^) assignable )?
    ;

implicationExpression
    : disjunctionExpression 
      ('=>'^ disjunctionExpression)?
    ;

//should '^' have a higher precedence?
disjunctionExpression
    : conjunctionExpression 
      ('||'^ conjunctionExpression)?
    ;

conjunctionExpression
    : logicalNegationExpression 
      ('&&'^ logicalNegationExpression)*
    ;

logicalNegationExpression
    : '!'^ logicalNegationExpression
    | equalityExpression
    ;

equalityExpression
    : comparisonExpression
      (('=='^|'!='^|'==='^) comparisonExpression)?
    ;

comparisonExpression
    : defaultExpression
      (('<=>'^ |'<'^ |'>'^ |'<='^ |'>='^ |'in'^ |'is'^) defaultExpression)?
    ;

//should we reverse the precedence order 
//of '?' and 'exists'/'nonempty'?
defaultExpression
    : existenceEmptinessExpression 
      ('?' defaultExpression)?
    ;

/*
existenceEmptinessExpression
    : e=rangeIntervalEntryExpression 
    ('exists' -> ^(EXISTS_EXPR $e) 
     | 'nonempty' -> ^(NONEMPTY_EXPR $e)
     | -> $e)
    ;
*/

existenceEmptinessExpression
    : e=rangeIntervalEntryExpression
       (('exists' -> ^(EXISTS_EXPR $e))
        | ('nonempty' -> ^(NONEMPTY_EXPR $e)) )?
     -> $e	
    ;

//I wonder if it would it be cleaner to give 
//'..' a higher precedence than '->'
rangeIntervalEntryExpression
    : additiveExpression
      (('..'^ |'->'^) additiveExpression)?
    ;

additiveExpression
    : multiplicativeExpression
      (('+'^ | '-'^ | '|'^ | '^'^) multiplicativeExpression)*
    ;

multiplicativeExpression 
    : exponentiationExpression
      (('*'^ | '/'^ | '%'^ | '&'^) exponentiationExpression)*
    ;

exponentiationExpression
    : unaryExpression ('**'^ unaryExpression)?
    ;

unaryExpression 
    : p=prefixOperator e=unaryExpression -> ^(PREFIX_EXPR $p $e)
    | primary
    ;

prefixOperator
    : '$' |'-' |'++' |'--' | '~'
    ;

primary
    : getterSetterMethodReference
    | prim
    ;	
    
prim
options {backtrack=true;}
/*
    : b=base 
    ((selector+
     -> ^(SELECTOR_LIST $b selector+))
    | -> $b
    )
*/
// This backtracking predicate really shouldn't be necessary, and the ANTLR
// book seems to agree, but the above doesn't work.
//    : base selector+ -> ^(SELECTOR_LIST base selector+)
//    | base
    :    
    base selector+ -> ^(EXPR base selector*)
    | base
    ;

getterSetterMethodReference
    :
    ('set' prim -> ^(SET_EXPR prim))
    | ('get' prim -> ^(GET_EXPR prim))
    ;

postfixOperator
    : ('--' | '++')
    ;	

base 
    : literal
    | parExpression
    | enumeration
    | specialValue
    | memberName
    | typeName
    //| inlineClassDeclaration
    ;
    

specialValue
    : 'this' 
    | 'super' 
    | 'null'
    | 'none'
    ;

enumeration
    : '{' assignables? '}'
    -> ^(ENUM_LIST assignables?)
    ;

selector 
    : member
    | arguments -> ^(CALL_EXPR arguments)
    | functionalArgument
    | elementSelector
    | postfixOperator -> ^(POSTFIX_EXPR postfixOperator)
    ;

member
    : ('.' | '^.' | '?.' | '*.') 
      ( memberName | typeName ) 
      ( (typeArguments '(') => typeArguments )?
    ;

elementSelector
    : '?'? '[' elementsSpec ']'
    -> ^(SUBSCRIPT_EXPR '?'? elementsSpec)
    ;

elementsSpec
    : lo=additiveExpression ( '...' | '..' hi=additiveExpression )?
    //|  '...' hi=additiveExpression)
    -> ^(LOWER_BOUND $lo)? ^(UPPER_BOUND $hi)?	
    ;

arguments 
    : positionalArguments | namedArguments
    ;
    
namedArgument
    : 'assign'? parameterName memberDefinition
    ;
    
parameterName
    : LIDENTIFIER -> ^(ARG_NAME LIDENTIFIER)
    ;

namedArguments
    : '{' ((namedArgument) => namedArgument)* varargArguments? '}'
    -> ^(ARG_LIST ^(NAMED_ARG namedArgument)* varargArguments?)
    ;

varargArguments
    : unnamedArg (','! unnamedArg)*
    ;

unnamedArg
    : assignable
    -> ^(UNNAMED_ARG assignable)
    ;

assignables
    : assignable (','! assignable)*
    ;

parExpression 
    : '(' assignable ')'
    ;
    
positionalArguments
    : '(' positionalArgumentList ')'
   -> ^(ARG_LIST positionalArgumentList?)
    ;

positionalArgumentList
    :
    ( positionalArgument (',' positionalArgument)* )? 
    -> positionalArgument*
    ;

positionalArgument
    : (variableStart) => specialArgument
    | assignable
    ;

//a smalltalk-style parameter to a positional parameter
//invocation
functionalArgument
    : functionalArgumentHeader functionalArgumentDefinition
    -> ^(NAMED_ARG functionalArgumentHeader? ^(ANON_METH functionalArgumentDefinition))
    ;
    
functionalArgumentHeader
    : parameterName -> ^(ARG_NAME parameterName)
    | 'case' '(' expressions ')' -> ^(CASE_ITEM ^(EXPR_LIST expressions))
    ;

functionalArgumentDefinition
    : ( (formalParameterStart) => formalParameters )? 
      ( block | parExpression /*| literal | specialValue*/ )
    ;

specialArgument
    : type memberName (containment|specifier)
    //| isCondition
    //| existsCondition
    ;

formalParameters
    : '(' (formalParameter (',' formalParameter)*)? ')'
    -> ^(ARG_LIST formalParameter*)
    ;

//special rule for syntactic predicates
//be careful with this one, since it 
//matches "()", which can also be an 
//argument list
formalParameterStart
    : '(' (declarationStart | ')')
    ;
    
formalParameter
    :
     formalParameter_ -> ^(FORMAL_PARAMETER formalParameter_)
    ;
    
// FIXME: This accepts more than the language spec: named arguments
// and varargs arguments can appear in any order.  We'll have to
// enforce the rule that the ... appears at the end of the parapmeter
// list in a later pass of the compiler.
formalParameter_
    : annotations?
      memberType ('...')? memberName
      memberParameters? 
      ( '->' type parameterName | '..' parameterName )? 
      specifier?
    ;

// Control structures.

condition
    : expression | existsCondition | nonemptyCondition | isCondition
    ;

// Backtracking here is needed for exactly the same reason as localOrStatement.
existsCondition
    : 'exists' ( (variableStart) => variable specifier | expression )
    -> ^(EXISTS_EXPR variable? specifier? expression?)
    ;
    
nonemptyCondition
    : 'nonempty' ( (variableStart) => variable specifier | expression )
    -> ^(NONEMPTY_EXPR variable? specifier? expression?)
    ;
    
isCondition
    : 'is' type ( (memberName '=') => memberName specifier 
                 -> ^(IS_EXPR type memberName specifier)
    | expression -> ^(IS_EXPR type expression))
    
    ;
    
controlStructure
    : ifElse | switchCaseElse | doWhile | forFail | tryCatchFinally
    ;
    
ifElse
    : 'if' '(' condition ')' ifBlock=block 
      ('else' (ifElse | elseBlock=block))?
    -> ^(IF_STMT ^(CONDITION condition) ^(IF_TRUE $ifBlock) ^(IF_FALSE $elseBlock? ifElse?)?)
    ;

switchCaseElse
    : 'switch' '(' expression ')' ( '{' cases '}' | cases )
    -> ^(SWITCH_STMT ^(SWITCH_EXPR expression) ^(SWITCH_CASE_LIST cases))
    ;
    
cases 
    : caseItem+ ('else' block)?
    -> caseItem+ ^(CASE_DEFAULT block)?
    ;
    
caseItem
    : ('case' '(' caseCondition ')' block)
    -> ^(CASE_ITEM caseCondition block)
    ;	 
    
caseCondition
    : expressions | ('is' type -> ^(IS_EXPR type))
    ;

expressions
    : expression (',' expression)*
    -> ^(EXPR_LIST expression+)
    ;

forFail
    : 'for' '(' forIterator ')' loopBlock=block ('fail' failBlock=block)?
    -> ^(FOR_STMT forIterator ^(LOOP_BLOCK $loopBlock) ^(FAIL_BLOCK $failBlock)?)
    ;

forIterator
    : v1=variable ('->' v2=variable)? containment
    -> ^(FOR_ITERATOR $v1 $v2? containment)
    ;
    
containment
    : 'in' expression
    -> ^(FOR_CONTAINMENT expression)
    ;
    
doWhile
    : ('do' ('(' doIterator ')')? b1=block? )?  
      'while' '(' condition ')' (b2=block | ';')
    -> ^(WHILE_STMT ^(CONDITION condition) ^(DO_ITERATOR doIterator)? ^(DO_BLOCK $b1)?
       ^(WHILE_BLOCK $b2)?)
    ;

//do iterators are allowed to be mutable and/or optional
doIterator
    : annotations? variable (specifier | initializer)
    ;

tryCatchFinally
    : tryStmt catchStmts finallyStmt
    -> ^(TRY_CATCH_STMT tryStmt catchStmts? finallyStmt?)
    ;
  
tryStmt
    : 'try' ( '(' resource (',' resource)* ')' )? block
    -> ^(TRY_STMT ^(TRY_RESOURCE_LIST resource)? ^(TRY_BLOCK block))
    ;
  
catchStmts
    : ('catch' '(' variable ')' block)*
    -> ^(CATCH_STMT variable ^(CATCH_BLOCK block))*
    ;
    
finallyStmt
    : ('finally' block)?
    -> ^(FINALLY_BLOCK block)?
    ;    	    

resource
    : (variableStart) => variable specifier 
    | expression
    ;

variable
    : type/*?*/ memberName
    ;

//special rule for syntactic predicate
variableStart
    : type/*?*/ memberName ('in'|'=')
    ;

// Lexer

NATURALLITERAL
    : Digit Digit*
    ;

fragment
Digit 
    : '0'..'9'
    ;

FLOATLITERAL
    :   ('0' .. '9')+ '.' ('0' .. '9')+ Exponent?  
    |   '.' ( '0' .. '9' )+ Exponent?  
    |   ('0' .. '9')+ Exponent  
    ;

fragment 
Exponent    
    :   ( 'e' | 'E' ) ( '+' | '-' )? ( '0' .. '9' )+ 
    ;

CHARLITERAL
    :   '@' ( ~ NonCharacterChars | EscapeSequence )
    ;

QUOTEDLITERAL
    :   '\''
        StringPart
        '\''
    ;

SIMPLESTRINGLITERAL
    :   '"'
        StringPart
        '"'
    ;

LEFTSTRINGLITERAL
    :   '"'
        StringPart
        '${'
    ;

RIGHTSTRINGLITERAL
    :   '}$'
        StringPart
        '"'
    ;

MIDDLESTRINGLITERAL
    :   '}$'
        StringPart
        '${'
    ;

fragment
NonStringChars
    :    '{' | '\\' | '"' | '$' | '\''
    ;

fragment
NonCharacterChars
    :    ' ' | '\\' | '\t' | '\n' | '\f' | '\r' | '\b'
    ;

fragment
StringPart
    :    
    ( ~ /* NonStringChars*/ ('{' | '\\' | '"' | '$' | '\'')
     | EscapeSequence) *
    ;
    
fragment
EscapeSequence 
    :   '\\' (
            'b' 
        |   't' 
        |   'n' 
        |   'f' 
        |   'r'
        |   's' 
        |   '\"' 
        |   '\''
        |   '$'
        |   '{'
        |   '}' 
        )          
    ;

WS  
    :   (
            ' '
        |    '\r'
        |    '\t'
        |    '\u000C'
        |    '\n'
        ) 
        {
            skip();
        }          
    ;

LINE_COMMENT
    :   '//' ~('\n'|'\r')*  ('\r\n' | '\r' | '\n') 
        {
            skip();
        }
    |   '//' ~('\n'|'\r')*
        {
            skip();
        }
    ;   

MULTI_COMMENT
        :       '/*'
                {
                        $channel=HIDDEN;
                }
                (       ~('/'|'*')
                        |       ('/' ~'*') => '/'
                        |       ('*' ~'/') => '*'
                        |       MULTI_COMMENT
                )*
                '*/'
        ;


ASSIGN
    :   'assign'
    ;
    
BREAK
    :   'break'
    ;
    
CASE
    :   'case'
    ;

CATCH
    :   'catch'
    ;

CLASS
    :   'class'
    ;

DECORATOR
    :   'decorator'
    ;

DO
    :   'do'
    ;
    
ELSE
    :   'else'
    ;            

EXISTS
    :   'exists'
    ;

FINALLY
    :   'finally'
    ;

FOR
    :   'for'
    ;
    
IF
    :   'if'
    ;

SATISFIES
    :   'satisfies'
    ;

IMPORT
    :   'import'
    ;

INTERFACE
    :   'interface'
    ;
    
NONE
    :   'none'
    ;

NULL
    :   'null'
    ;

NONEMPTY
    :   'nonempty'
    ;

GET
    :   'get'
    ;

SET
    :   'set'
    ;

RETURN
    :   'return'
    ;

SUPER
    :   'super'
    ;

SWITCH
    :   'switch'
    ;

THIS
    :   'this'
    ;

SUBTYPE
    :   'subtype'
    ;

THROW
    :   'throw'
    ;

TRY
    :   'try'
    ;

RETRY
    : 'retry'
    ;

VOID
    :   'void'
    ;

WHILE
    :   'while'
    ;

LPAREN
    :   '('
    ;

RPAREN
    :   ')'
    ;

LBRACE
    :   '{'
    ;

RBRACE
    :   '}'
    ;

LBRACKET
    :   '['
    ;

RBRACKET
    :   ']'
    ;

SEMI
    :   ';'
    ;

COMMA
    :   ','
    ;

DOT
    :   '.'
    ;

ELLIPSIS
    :   '...'
    ;

EQ
    :   '='
    ;

BANG
    :   '!'
    ;

TILDE
    :   '~'
    ;

QUES
    :   '?'
    ;

COLON
    :   ':'
    ;
    
COLONEQ
    :   ':='
    ;

EQEQ
    :   '=='
    ;

AMPAMP
    :   '&&'
    ;

BARBAR
    :   '||'
    ;

PLUSPLUS
    :   '++'
    ;

SUBSUB
    :   '--'
    ;

PLUS
    :   '+'
    ;

SUB
    :   '-'
    ;

STAR
    :   '*'
    ;

SLASH
    :   '/'
    ;

AMP
    :   '&'
    ;

BAR
    :   '|'
    ;

CARET
    :   '^'
    ;

PERCENT
    :   '%'
    ;

BANGEQ
    :   '!='
    ;

GT
    :   '>'
    ;

LT
    :   '<'
    ;        

ENTRY
    :   '->'
    ;

RANGE
    :   '..'
    ;
    
COMPARE
    :   '<=>'
    ;
    
IN
    :   'in'
    ;

IS
    :   'is'
    ;

HASH
    :   '#'
    ;

PLUSEQ
    :   '+='
    ;
    
CONVERTER
    :  'converter'
    ;
    
LIDENTIFIER 
    :   LIdentifierPart IdentifierPart*
    ;

UIDENTIFIER 
    :   UIdentifierPart IdentifierPart*
    ;

// FIXME: Unicode identifiers
fragment
LIdentifierPart
    :   '_'
    |   'a'..'z'
    ;       
                       
// FIXME: Unicode identifiers
fragment
UIdentifierPart
    :   'A'..'Z'
    ;       
                       
fragment 
IdentifierPart
    :   LIdentifierPart 
    |   UIdentifierPart
    |   '0'..'9'
    ;
