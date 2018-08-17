package org.vclang.lang.lexer;

import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.vclang.vclpsi.VclElementTypes.*;
import com.intellij.lexer.FlexLexer;

%%

%public
%class VclLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL                 = \R
WHITE_SPACE         = \s+

START_CHAR          = [a-zA-Z_]
ID = {START_CHAR}({START_CHAR} | [0-9'])*
MODNAME = {ID}(.{ID})*
DIRNAME = ([\w]\: (\\\\|\\))? ([a-zA-Z0-9\.\-\_\\]+) | (\/)? ([a-zA-Z0-9\.\-\_\/]+)
LIBNAME = {START_CHAR}({START_CHAR} | [0-9'] | \-)*


%%

<YYINITIAL> {
    {WHITE_SPACE}           { return WHITE_SPACE; }
    "dependencies"          { return DEPS; }
    "sourcesDir"            { return SOURCE; }
    "binariesDir"           { return BINARY; }
    "modules"               { return MODULES; }
    ":"                     { return COLON; }
    {MODNAME}               { return MODNAME; }
    {DIRNAME}               { return DIRNAME; }
    {LIBNAME}               { return LIBNAME; }
}
