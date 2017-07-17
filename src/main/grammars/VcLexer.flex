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

EOL=\R
WHITE_SPACE=\s+

ID=[:letter:][a-zA-Z_0-9]*'*(-[:letter:][a-zA-Z_0-9]*'*)*
NUMBER=[0-9]+
BIN_OP=[~!@#$%\^&*\\\-+=<>?/|.:]+
SET=\\Set[0-9]*
UNIVERSE=\\Type[0-9]*
TRUNCATED_UNIVERSE=\\([0-9]+|oo)-Type[0-9]*
LINE_COMMENT=--.*
BLOCK_COMMENT=\{-[^]*-\}

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
  "{?}"                     { return HOLE; }
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

  {ID}                      { return ID; }
  {NUMBER}                  { return NUMBER; }
  {BIN_OP}                  { return BIN_OP; }
  {SET}                     { return SET; }
  {UNIVERSE}                { return UNIVERSE; }
  {TRUNCATED_UNIVERSE}      { return TRUNCATED_UNIVERSE; }
  {LINE_COMMENT}            { return LINE_COMMENT; }
  {BLOCK_COMMENT}           { return BLOCK_COMMENT; }

}

[^] { return BAD_CHARACTER; }
