package org.vclang.lang.lexer;

import static org.vclang.psi.VclElementTypes.*;
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

%%

