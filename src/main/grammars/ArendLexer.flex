package org.arend.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.arend.parser.ParserMixin.*;
import static org.arend.psi.ArendElementTypes.*;

%%

%{
    public ArendLexer() {
        this((java.io.Reader)null);
    }
%}

%public
%class ArendLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%{
    private int commentStart;
    private int commentDepth;
    private boolean isInsideDocComment;
    private int originalState = YYINITIAL;
%}

%state BLOCK_COMMENT_INNER, LEVEL_PARAMETERS

EOL                 = \R
WHITE_SPACE         = [ \t\r\n]+

LINE_COMMENT        = -- ([ ] ([^\|\r\n] .* | {EOL})? | ([^ ~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_0-9'\u2200-\u22FF\u2A00-\u2AFF\r\n] .* | {EOL})? | -+ ([^~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_0-9'\u2200-\u22FF\u2A00-\u2AFF\r\n] .* | {EOL})?)
BLOCK_DOC_COMMENT_START = "{- |"
BLOCK_COMMENT_START = "{-"
BLOCK_COMMENT_END   = "-}"

LINE_DOC  = "-- |" (.* | {EOL})

NUMBER              = [0-9]+
NEGATIVE_NUMBER     = -{NUMBER}

START_CHAR          = [~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_\u2200-\u22FF\u2A00-\u2AFF]
KEYWORD             = \\[0-9]*{ID}

ID_CHAR             = {START_CHAR} | [0-9']
ID                  = {START_CHAR} {ID_CHAR}*
POSTFIX             = `{ID}
INFIX               = `{ID}`

SET                 = \\Set[0-9]*
UNIVERSE            = \\Type[0-9]*
TRUNCATED_UNIVERSE  = \\([0-9]+-|oo-|h)Type[0-9]*

STRING              = \"{STRING_CONTENT}*\"
STRING_CONTENT      = [^\"\\\r\n] | \\[btnfr\"\'\\] | {OCT_ESCAPE} | {UNICODE_ESCAPE}
OCT_ESCAPE          = \\{OCT_DIGIT}{OCT_DIGIT}? | \\[0-3]{OCT_DIGIT}{2}
UNICODE_ESCAPE      = \\u+{HEX_DIGIT}{4}
HEX_DIGIT           = [0-9a-fA-F]
OCT_DIGIT           = [0-8]

%%

<YYINITIAL,LEVEL_PARAMETERS> {
    {WHITE_SPACE}           { return WHITE_SPACE; }

    {LINE_COMMENT}          { return LINE_COMMENT; }
    {BLOCK_COMMENT_START}   {
                                originalState = yystate();
                                yybegin(BLOCK_COMMENT_INNER);
                                isInsideDocComment = false;
                                commentDepth = 0;
                                commentStart = getTokenStart();
                            }
    {BLOCK_DOC_COMMENT_START} {
                                originalState = yystate();
                                yybegin(BLOCK_COMMENT_INNER);
                                isInsideDocComment = true;
                                commentDepth = 0;
                                commentStart = getTokenStart();
                            }
    {LINE_DOC}              { return DOC_COMMENT; }
}

<YYINITIAL> {
    "{"                     { return LBRACE; }
    "}"                     { return RBRACE; }
    "{?}"                   { return TGOAL; }
    "{?"                    { return LGOAL; }
    "("                     { return LPAREN; }
    ")"                     { return RPAREN; }
    ":"                     { return COLON; }
    "->"                    { return ARROW; }
    "=>"                    { return FAT_ARROW; }
    "."                     { return DOT; }
    ","                     { return COMMA; }
    "__"                    { return APPLY_HOLE; }
    "_"                     { return UNDERSCORE; }
    "|"                     { return PIPE; }

    "\\open"                { return OPEN_KW; }
    "\\import"              { return IMPORT_KW; }
    "\\hiding"              { return HIDING_KW; }
    "\\using"               { return USING_KW; }
    "\\as"                  { return AS_KW; }
    "\\module"              { return MODULE_KW; }
    "\\func"                { return FUNC_KW; }
    "\\axiom"               { return AXIOM_KW; }
    "\\sfunc"               { return SFUNC_KW; }
    "\\lemma"               { return LEMMA_KW; }
    "\\type"                { return TYPE_KW; }
    "\\meta"                { return META_KW; }
    "\\coerce"              { return COERCE_KW; }
    "\\use"                 { return USE_KW; }
    "\\field"               { return FIELD_KW; }
    "\\override"            { return OVERRIDE_KW; }
    "\\default"             { return DEFAULT_KW; }
    "\\property"            { return PROPERTY_KW; }
    "\\classifying"         { return CLASSIFYING_KW; }
    "\\noclassifying"       { return NO_CLASSIFYING_KW; }
    "\\alias"               { return ALIAS_KW; }
    "\\infix"               { return INFIX_NON_KW; }
    "\\infixl"              { return INFIX_LEFT_KW; }
    "\\infixr"              { return INFIX_RIGHT_KW; }
    "\\fix"                 { return NON_ASSOC_KW; }
    "\\fixl"                { return LEFT_ASSOC_KW; }
    "\\fixr"                { return RIGHT_ASSOC_KW; }
    "\\Prop"                { return PROP_KW; }
    "\\this"                { return THIS_KW; }
    "\\where"               { return WHERE_KW; }
    "\\with"                { return WITH_KW; }
    "\\cowith"              { return COWITH_KW; }
    "\\elim"                { return ELIM_KW; }
    "\\new"                 { return NEW_KW; }
    "\\Pi"                  { return PI_KW; }
    "\\Sigma"               { return SIGMA_KW; }
    "\\lam"                 { return LAM_KW; }
    "\\have"                { return HAVE_KW; }
    "\\have!"               { return HAVES_KW; }
    "\\let"                 { return LET_KW; }
    "\\let!"                { return LETS_KW; }
    "\\in"                  { return IN_KW; }
    "\\case"                { return CASE_KW; }
    "\\scase"               { return SCASE_KW; }
    "\\eval"                { return EVAL_KW; }
    "\\peval"               { return PEVAL_KW; }
    "\\box"                 { return BOX_KW; }
    "\\return"              { return RETURN_KW; }
    "\\data"                { return DATA_KW; }
    "\\cons"                { return CONS_KW; }
    "\\class"               { return CLASS_KW; }
    "\\record"              { return RECORD_KW; }
    "\\extends"             { return EXTENDS_KW; }
    "\\instance"            { return INSTANCE_KW; }
    "\\truncated"           { return TRUNCATED_KW; }
    "\\strict"              { return STRICT_KW; }
    "\\private"             { return PRIVATE_KW; }
    "\\protected"           { return PROTECTED_KW; }
    "\\lp"                  { return LP_KW; }
    "\\lh"                  { return LH_KW; }
    "\\oo"                  { return OO_KW; }
    "\\suc"                 { return SUC_KW; }
    "\\level"               { return LEVEL_KW; }
    "\\levels"              { return LEVELS_KW; }
    "\\plevels"             { yybegin(LEVEL_PARAMETERS); return PLEVELS_KW; }
    "\\hlevels"             { yybegin(LEVEL_PARAMETERS); return HLEVELS_KW; }
    "\\max"                 { return MAX_KW; }

    {STRING}                { return STRING; }

    {SET}                   { return SET; }
    {UNIVERSE}              { return UNIVERSE; }
    {TRUNCATED_UNIVERSE}    { return TRUNCATED_UNIVERSE; }

    {KEYWORD}               { return INVALID_KW; }

    {NUMBER}                { return NUMBER; }
    {NEGATIVE_NUMBER}       { return NEGATIVE_NUMBER; }

    {POSTFIX}               { return POSTFIX; }
    {INFIX}                 { return INFIX; }
    // For REPL
    :{ID}                   { return getTokenStart() == 0 ? REPL_COMMAND : ID; }
    {ID}                    { return ID; }

    [^]                     { return BAD_CHARACTER; }
}

<LEVEL_PARAMETERS> {
    "<="                    { return LESS_OR_EQUALS; }
    ">="                    { return GREATER_OR_EQUALS; }
    ","                     { return COMMA; }
    "|" | ":" | "_"         { yypushback(1); yybegin(YYINITIAL); }
    "=>" | "->" | "__"      { yypushback(2); yybegin(YYINITIAL); }
    {ID}                    { return ID; }
    [^]                     { yypushback(1); yybegin(YYINITIAL); }
}

<BLOCK_COMMENT_INNER> {
    {BLOCK_COMMENT_START} {
        commentDepth++;
    }

    {BLOCK_DOC_COMMENT_START} {
        commentDepth++;
    }

    {BLOCK_COMMENT_END} {
        if (commentDepth > 0) {
            commentDepth--;
        } else {
             zzStartRead = commentStart;
             yybegin(originalState);
             return isInsideDocComment ? DOC_COMMENT : BLOCK_COMMENT;
        }
    }

    <<EOF>> {
        zzStartRead = commentStart;
        yybegin(originalState);
        return isInsideDocComment ? DOC_COMMENT : BLOCK_COMMENT;
    }

    [^] {}
}
