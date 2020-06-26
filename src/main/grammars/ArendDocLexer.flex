package org.arend.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static org.arend.parser.ParserMixin.*;
import static org.arend.psi.ArendElementTypes.*;

%%

%{
    public ArendDocLexer() {
        this((java.io.Reader)null);
    }
%}

%public
%class ArendDocLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%{
    private int textStart;
%}

%state CONTENTS
%state TEXT
%state CODE1
%state CODE2
%state CODE3
%state REFERENCE

START_CHAR          = [~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_\u2200-\u22FF]
ID_CHAR             = {START_CHAR} | [0-9']
ID                  = {START_CHAR} {ID_CHAR}*

%%

<YYINITIAL> {
    ("-- |" | "{- |") {
        yybegin(CONTENTS);
        return DOC_START;
    }
}

<CONTENTS> {
    "{" {
        yybegin(REFERENCE);
        return DOC_IGNORED;
    }
    "`" {
        textStart = getTokenStart();
        yybegin(CODE1);
        return DOC_IGNORED;
    }
    "``" {
        textStart = getTokenStart();
        yybegin(CODE2);
        return DOC_IGNORED;
    }
    "```" {
        textStart = getTokenStart();
        yybegin(CODE3);
        return DOC_IGNORED;
    }
    "-}" {
        if (zzMarkedPos == zzBuffer.length()) {
            yybegin(YYINITIAL);
            return DOC_IGNORED;
        } else {
            textStart = getTokenStart();
            yybegin(TEXT);
        }
    }
    [^] {
        textStart = getTokenStart();
        yybegin(TEXT);
    }
}

<TEXT> {
    ("{" | "`") {
        zzMarkedPos--;
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_TEXT;
    }
    [^] {}
}

<REFERENCE> {
    "}" {
        yybegin(CONTENTS);
        return DOC_IGNORED;
    }
    "." { return DOT; }
    {ID} { return ID; }
}

<CODE1> {
    ("`" | "\n") {
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_CODE;
    }
    [^] {}
}

<CODE2> {
    ("``" | "\n") {
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_CODE;
    }
    [^] {}
}

<CODE3> {
    "```" {
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_CODE;
    }
    [^] {}
}

<TEXT,CODE1,CODE2,CODE3> {
    "-}" {
        if (zzMarkedPos == zzBuffer.length()) {
            zzMarkedPos -= 2;
            zzStartRead = textStart;
            boolean isText = yystate() == TEXT;
            yybegin(CONTENTS);
            return isText ? DOC_TEXT : DOC_CODE;
        }
    }

    <<EOF>> {
        zzStartRead = textStart;
        boolean isText = yystate() == TEXT;
        yybegin(YYINITIAL);
        return isText ? DOC_TEXT : DOC_CODE;
    }
}

[^] { return BAD_CHARACTER; }
