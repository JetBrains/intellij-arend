package org.vclang.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.vclang.psi.VcElementTypes.*;

%%

%{
    public _VcLexer() {
        this((java.io.Reader)null);
    }
%}

%public
%class _VcLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%{
    private int commentStart;
    private int commentDepth;
%}

%state BLOCK_COMMENT_INNER

EOL                 = \R
WHITE_SPACE         = \s+

LINE_COMMENT        = --(.*|{EOL})
BLOCK_COMMENT_START = \{-
BLOCK_COMMENT_END   = -\}

NUMBER              = [0-9]+

START_CHAR          = [~!@#$%\^&*\-+=<>?/|\[\];:a-zA-Z_]
KEYWORD             = \\[0-9]*{ID}

ID                  = {START_CHAR} ({START_CHAR} | [0-9'])*
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
    "\\func"                { return FUNCTION_KW; }
    "\\infix"               { return INFIX_NON_KW; }
    "\\infixl"              { return INFIX_LEFT_KW; }
    "\\infixr"              { return INFIX_RIGHT_KW; }
    "\\fix"                 { return NON_ASSOC_KW; }
    "\\fixl"                { return LEFT_ASSOC_KW; }
    "\\fixr"                { return RIGHT_ASSOC_KW; }
    "\\Prop"                { return PROP_KW; }
    "\\where"               { return WHERE_KW; }
    "\\with"                { return WITH_KW; }
    "\\elim"                { return ELIM_KW; }
    "\\new"                 { return NEW_KW; }
    "\\Pi"                  { return PI_KW; }
    "\\Sigma"               { return SIGMA_KW; }
    "\\lam"                 { return LAM_KW; }
    "\\let"                 { return LET_KW; }
    "\\in"                  { return IN_KW; }
    "\\case"                { return CASE_KW; }
    "\\data"                { return DATA_KW; }
    "\\class"               { return CLASS_KW; }
    "\\extends"             { return EXTENDS_KW; }
    "\\instance"            { return INSTANCE_KW; }
    "\\truncated"           { return TRUNCATED_KW; }
    "\\lp"                  { return LP_KW; }
    "\\lh"                  { return LH_KW; }
    "\\suc"                 { return SUC_KW; }
    "\\levels"              { return LEVELS_KW; }
    "\\max"                 { return MAX_KW; }

    {SET}                   { return SET; }
    {UNIVERSE}              { return UNIVERSE; }
    {TRUNCATED_UNIVERSE}    { return TRUNCATED_UNIVERSE; }

    {KEYWORD}               { return INVALID_KW; }

    {NUMBER}                { return NUMBER; }

    {POSTFIX}               { return POSTFIX; }
    {INFIX}                 { return INFIX; }
    {ID}                    { return ID; }

    {LINE_COMMENT}          { return LINE_COMMENT; }
    {BLOCK_COMMENT_START}   {
                                yybegin(BLOCK_COMMENT_INNER);
                                commentDepth = 0;
                                commentStart = getTokenStart();
                            }
    {BLOCK_COMMENT_END}     { return BLOCK_COMMENT_END; }
}

<BLOCK_COMMENT_INNER> {
    {BLOCK_COMMENT_START} {
        commentDepth++;
    }

    {BLOCK_COMMENT_END} {
        if (commentDepth > 0) {
            commentDepth--;
        } else {
             int state = yystate();
             yybegin(YYINITIAL);
             zzStartRead = commentStart;
             return BLOCK_COMMENT;
        }
    }

    <<EOF>> {
        int state = yystate();
        yybegin(YYINITIAL);
        zzStartRead = commentStart;
        return BLOCK_COMMENT;
    }

    [^] {}
}

[^] { return BAD_CHARACTER; }
