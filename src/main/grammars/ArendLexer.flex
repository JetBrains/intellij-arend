package org.arend.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
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
    private int docCommentStart;
    private int commentDepth;
    private boolean isInsideDocComment;
%}

%state BLOCK_COMMENT_INNER
%state BLOCK_DOC_COMMENT_INNER
%state LINE_DOC_COMMENT_INNER

EOL                 = \R
WHITE_SPACE         = [ \t\r\n]+

LINE_COMMENT        = -- (([ ] [^\|] .* | {EOL})? | ([\t`\\] .*) | (-+ ([ \t] .* | {EOL})?))
BLOCK_COMMENT_START = "{-"
BLOCK_COMMENT_END   = "-}"

LINE_DOC_COMMENT_START  = -- " " \|
BLOCK_DOC_COMMENT_START = \{- " " \|

BLOCK_DOC_TEXT = ([^\{-] | -[^\}] | \{[^-])+
LINE_DOC_TEXT = (.*|{EOL})

NUMBER              = [0-9]+
NEGATIVE_NUMBER     = -[0-9]+

START_CHAR          = [~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_]
KEYWORD             = \\[0-9]*{ID}

ID_CHAR             = {START_CHAR} | [0-9']
ID                  = {START_CHAR} {ID_CHAR}*
POSTFIX             = `{ID}
INFIX               = `{ID}`

SET                 = \\Set[0-9]*
UNIVERSE            = \\Type[0-9]*
TRUNCATED_UNIVERSE  = \\([0-9]+|oo)-Type[0-9]*

%%
<YYINITIAL> {
    {WHITE_SPACE}           { return WHITE_SPACE; }

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
    "_"                     { return UNDERSCORE; }
    "|"                     { return PIPE; }

    "\\open"                { return OPEN_KW; }
    "\\import"              { return IMPORT_KW; }
    "\\hiding"              { return HIDING_KW; }
    "\\using"               { return USING_KW; }
    "\\as"                  { return AS_KW; }
    "\\module"              { return MODULE_KW; }
    "\\func"                { return FUNCTION_KW; }
    "\\lemma"               { return LEMMA_KW; }
    "\\coerce"              { return COERCE_KW; }
    "\\use"                 { return USE_KW; }
    "\\field"               { return FIELD_KW; }
    "\\property"            { return PROPERTY_KW; }
    "\\classifying"         { return CLASSIFYING_KW; }
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
    "\\let"                 { return LET_KW; }
    "\\in"                  { return IN_KW; }
    "\\case"                { return CASE_KW; }
    "\\return"              { return RETURN_KW; }
    "\\data"                { return DATA_KW; }
    "\\class"               { return CLASS_KW; }
    "\\record"              { return RECORD_KW; }
    "\\extends"             { return EXTENDS_KW; }
    "\\instance"            { return INSTANCE_KW; }
    "\\truncated"           { return TRUNCATED_KW; }
    "\\lp"                  { return LP_KW; }
    "\\lh"                  { return LH_KW; }
    "\\suc"                 { return SUC_KW; }
    "\\level"               { return LEVEL_KW; }
    "\\max"                 { return MAX_KW; }

    {LINE_COMMENT}          { return LINE_COMMENT; }
    {BLOCK_COMMENT_START}   {
                                yybegin(BLOCK_COMMENT_INNER);
                                isInsideDocComment = false;
                                commentDepth = 0;
                                commentStart = getTokenStart();
                            }
    {BLOCK_COMMENT_END}     { return BLOCK_COMMENT_END; }

    {LINE_DOC_COMMENT_START}
                            { yybegin(LINE_DOC_COMMENT_INNER);
                              return LINE_DOC_COMMENT_START; }
    {BLOCK_DOC_COMMENT_START}
                            { yybegin(BLOCK_DOC_COMMENT_INNER);
                              isInsideDocComment = true;
                              docCommentStart = getTokenStart(); }

    {SET}                   { return SET; }
    {UNIVERSE}              { return UNIVERSE; }
    {TRUNCATED_UNIVERSE}    { return TRUNCATED_UNIVERSE; }

    {KEYWORD}               { return INVALID_KW; }

    {NUMBER}                { return NUMBER; }
    {NEGATIVE_NUMBER}       { return NEGATIVE_NUMBER; }

    {POSTFIX}               { return POSTFIX; }
    {INFIX}                 { return INFIX; }
    {ID}                    { return ID; }
}

<LINE_DOC_COMMENT_INNER> {
    {LINE_DOC_TEXT} {
        yybegin(YYINITIAL);
        return LINE_DOC_TEXT;
    }

    [^] {}
 }

<BLOCK_DOC_COMMENT_INNER> {
    {BLOCK_DOC_TEXT} { return BLOCK_DOC_TEXT; }

    {BLOCK_COMMENT_START} {
          yybegin(BLOCK_COMMENT_INNER);
          commentDepth = 0;
          commentStart = getTokenStart();
    }

    {BLOCK_DOC_COMMENT_START} {
          yybegin(BLOCK_COMMENT_INNER);
          commentDepth = 0;
          commentStart = getTokenStart();
    }

    {BLOCK_COMMENT_END} {
        zzStartRead = commentStart;
        yybegin(YYINITIAL);
        return BLOCK_COMMENT_END;
    }

    <<EOF>> {
        yybegin(YYINITIAL);
        return BLOCK_COMMENT_END;
    }

    [^] {}
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
             int state = yystate();
             zzStartRead = commentStart;
             if (isInsideDocComment) {
                 yybegin(BLOCK_DOC_COMMENT_INNER);
             } else {
                 yybegin(YYINITIAL);
             }
             return BLOCK_COMMENT;
        }
    }

    <<EOF>> {
        int state = yystate();
        if (isInsideDocComment) {
            yybegin(BLOCK_DOC_COMMENT_INNER);
        } else {
            yybegin(YYINITIAL);
        }
        zzStartRead = commentStart;
        return BLOCK_COMMENT;
    }

    [^] {}
}

[^] { return BAD_CHARACTER; }
