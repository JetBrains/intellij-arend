package org.vclang.lexer;

import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
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

%{
    private int commentStart;
    private int commentDepth;
    private int originalState = YYINITIAL;
%}

%state BLOCK_COMMENT_INNER
%state LIBRARIES_STATE
%state DIRECTORY_STATE
%state MODULES_STATE

EOL                 = \R
WHITE_SPACE         = \s+

LINE_COMMENT        = -- -* ([ \t] (.*|{EOL}))? {EOL}?
BLOCK_COMMENT_START = \{-
BLOCK_COMMENT_END   = -\}

START_CHAR          = [a-zA-Z_]
ID = {START_CHAR}({START_CHAR} | [0-9'])*
MODULE_NAME = {ID}(\.{ID})*
DIRECTORY_NAME = [^\s:{\-] (.*|{EOL})
LIBRARY_NAME = {START_CHAR}({START_CHAR} | [0-9'\-\.])*


%%

<YYINITIAL,LIBRARIES_STATE,DIRECTORY_STATE,MODULES_STATE> {
    {WHITE_SPACE}           { return WHITE_SPACE; }
    "dependencies"          { yybegin(LIBRARIES_STATE);
                              return DEPS;
                            }
    "sourcesDir"            { yybegin(DIRECTORY_STATE);
                              return SOURCE;
                            }
    "binariesDir"           { yybegin(DIRECTORY_STATE);
                              return BINARY;
                            }
    "modules"               { yybegin(MODULES_STATE);
                              return MODULES;
                            }

    {LINE_COMMENT}          { return LINE_COMMENT; }
    {BLOCK_COMMENT_START}   { originalState = yystate();
                              yybegin(BLOCK_COMMENT_INNER);
                              commentDepth = 0;
                              commentStart = getTokenStart();
                            }
    {BLOCK_COMMENT_END}     { return BLOCK_COMMENT_END; }
}

<YYINITIAL> {
    {ID}                    { return ID; }
}

<LIBRARIES_STATE,DIRECTORY_STATE,MODULES_STATE> {
    ":"                     { return COLON; }
}

<LIBRARIES_STATE> {
    {LIBRARY_NAME}          { return LIBRARY_NAME; }
}

<DIRECTORY_STATE> {
    {DIRECTORY_NAME}        { originalState = YYINITIAL;
                              yybegin(YYINITIAL);
                              return DIRECTORY_NAME; }
}

<MODULES_STATE> {
    {MODULE_NAME}           { return MODULE_NAME; }
}

<BLOCK_COMMENT_INNER> {
    {BLOCK_COMMENT_START} {
        commentDepth++;
    }

    {BLOCK_COMMENT_END} {
        if (commentDepth > 0) {
            commentDepth--;
        } else {
             yybegin(originalState);
             zzStartRead = commentStart;
             return BLOCK_COMMENT;
        }
    }

    <<EOF>> {
        yybegin(YYINITIAL);
        zzStartRead = commentStart;
        return BLOCK_COMMENT;
    }

    [^] {}
}

[^] { return BAD_CHARACTER; }
