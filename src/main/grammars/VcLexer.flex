package org.vclang.lang.core.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.vclang.lang.core.psi.VcTypes.*;

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

INFIX               = [~!@#$%\^&*\-+=<>?/|:;\[\]]+
PREFIX              = [a-zA-Z_][~!@#$%\^&*\-+=<>?/|:;\[\]a-zA-Z0-9_']*
NUMBER              = [0-9]+
MODULE_NAME         = ::[a-zA-Z_][a-zA-Z0-9_']*

SET                 = \\Set[0-9]*
UNIVERSE            = \\Type[0-9]*
TRUNCATED_UNIVERSE  = \\([0-9]+|oo)-Type[0-9]*

LINE_COMMENT        = --(.*|{EOL})
BLOCK_COMMENT_START = \{-
BLOCK_COMMENT_END   = -\}

%%
<YYINITIAL> {
  {WHITE_SPACE}             { return WHITE_SPACE; }

  "{"                       { return LBRACE; }
  "}"                       { return RBRACE; }
  "("                       { return LPAREN; }
  ")"                       { return RPAREN; }
  ":"                       { return COLON; }
  "::"                      { return COLONCOLON; }
  "->"                      { return ARROW; }
  "=>"                      { return FAT_ARROW; }
  "."                       { return DOT; }
  ","                       { return COMMA; }
  "_"                       { return UNDERSCORE; }
  "`"                       { return GRAVE; }
  "|"                       { return PIPE; }

  "\\open"                  { return OPEN_KW; }
  "\\export"                { return EXPORT_KW; }
  "\\hiding"                { return HIDING_KW; }
  "\\function"              { return FUNCTION_KW; }
  "\\infix"                 { return NON_ASSOC_KW; }
  "\\infixl"                { return LEFT_ASSOC_KW; }
  "\\infixr"                { return RIGHT_ASSOC_KW; }
  "\\Prop"                  { return PROP_KW; }
  "\\where"                 { return WHERE_KW; }
  "\\with"                  { return WITH_KW; }
  "\\elim"                  { return ELIM_KW; }
  "\\field"                 { return FIELD_KW; }
  "\\new"                   { return NEW_KW; }
  "\\Pi"                    { return PI_KW; }
  "\\Sigma"                 { return SIGMA_KW; }
  "\\lam"                   { return LAM_KW; }
  "\\let"                   { return LET_KW; }
  "\\in"                    { return IN_KW; }
  "\\case"                  { return CASE_KW; }
  "\\implement"             { return IMPLEMENT_KW; }
  "\\data"                  { return DATA_KW; }
  "\\class"                 { return CLASS_KW; }
  "\\extends"               { return EXTENDS_KW; }
  "\\view"                  { return VIEW_KW; }
  "\\on"                    { return ON_KW; }
  "\\by"                    { return BY_KW; }
  "\\instance"              { return INSTANCE_KW; }
  "\\truncated"             { return TRUNCATED_KW; }
  "\\default"               { return DEFAULT_KW; }
  "\\lp"                    { return LP_KW; }
  "\\lh"                    { return LH_KW; }
  "\\suc"                   { return SUC_KW; }
  "\\max"                   { return MAX_KW; }

  {INFIX}                   { return INFIX; }
  {PREFIX}                  { return PREFIX; }
  {NUMBER}                  { return NUMBER; }
  {MODULE_NAME}             { return MODULE_NAME; }
  {SET}                     { return SET; }
  {UNIVERSE}                { return UNIVERSE; }
  {TRUNCATED_UNIVERSE}      { return TRUNCATED_UNIVERSE; }
  {LINE_COMMENT}            { return LINE_COMMENT; }

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
