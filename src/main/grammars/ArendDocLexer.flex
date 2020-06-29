package org.arend.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.*;
import static org.arend.parser.ParserMixin.*;
import static org.arend.psi.ArendElementTypes.*;

%%

%{
    public ArendDocLexer() {
        this((java.io.Reader)null);
    }

    private int textStart;

    private boolean checkNextToLast() {
      if (zzMarkedPos == zzBuffer.length() - 1) {
        zzMarkedPos--;
        return true;
      } else {
        return false;
      }
    }
%}

%public
%class ArendDocLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%state CONTENTS
%state TEXT
%state CODE1
%state CODE2
%state CODE3
%state CLOSE_CODE1
%state CLOSE_CODE2
%state CLOSE_CODE3
%state REFERENCE
%state REFERENCE_TEXT

START_CHAR          = [~!@#$%\^&*\-+=<>?/|\[\]:a-zA-Z_\u2200-\u22FF]
ID_CHAR             = {START_CHAR} | [0-9']
ID                  = {START_CHAR} {ID_CHAR}*

NEW_LINE            = "\n" [ \t]* ("-" [ \t]*)?
PARAGRAPH_SEP       = {NEW_LINE} ("\r"? {NEW_LINE})+
CODE_NEW_LINE       = "\n" [ \t]* "-"?
NEW_LINE_HYPHEN     = "\n" [ \t]* "-"

%%

<YYINITIAL> {
    "-- |" {
        yybegin(CONTENTS);
        return DOC_SINGLE_LINE_START;
    }
    "{- |" {
        yybegin(CONTENTS);
        return DOC_START;
    }
}

<CONTENTS> {
    "{" {
        yybegin(REFERENCE);
        return LBRACE;
    }
    "[" {
        textStart = zzMarkedPos;
        yybegin(REFERENCE_TEXT);
        return DOC_LBRACKET;
    }
    "]" {
        return DOC_RBRACKET;
    }
    "`" {
            textStart = getTokenStart();
            yybegin(CODE1);
            return DOC_INLINE_CODE_BORDER;
        }
    "``" {
            textStart = getTokenStart();
            yybegin(CODE2);
            return DOC_INLINE_CODE_BORDER;
        }
    "```" {
        textStart = getTokenStart();
        yybegin(CODE3);
        return DOC_CODE_BLOCK_BORDER;
    }
    "-}" {
            if (zzMarkedPos == zzBuffer.length()) {
                yybegin(YYINITIAL);
                return DOC_END;
            } else {
                textStart = getTokenStart();
                yybegin(TEXT);
            }
        }
    {PARAGRAPH_SEP} {
        return DOC_PARAGRAPH_SEP;
    }
    {NEW_LINE_HYPHEN} {
        checkNextToLast();
        return WHITE_SPACE;
    }
    {NEW_LINE} {
        return WHITE_SPACE;
    }
    [^] {
        textStart = getTokenStart();
        yybegin(TEXT);
    }
}

<TEXT> {
    ("{" | "[" | "`" | {NEW_LINE}) {
        zzMarkedPos = zzStartRead;
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_TEXT;
    }
    [^] {}
}

<REFERENCE> {
    "}" {
        yybegin(CONTENTS);
        return RBRACE;
    }
    "." { return DOT; }
    {ID} { return ID; }
}

<REFERENCE_TEXT> {
    "]" {
        zzMarkedPos--;
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_TEXT;
    }
    [^] {}
}

<CODE1> {
    "`" {
        zzMarkedPos--;
        zzStartRead = textStart;
        yybegin(CLOSE_CODE1);
        return DOC_CODE;
    }
    "\n" {
        zzMarkedPos--;
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_CODE;
    }
    [^] {}
}

<CODE2> {
    "``" {
        zzMarkedPos -= 2;
        zzStartRead = textStart;
        yybegin(CLOSE_CODE2);
        return DOC_CODE;
     }
    "\n" {
        zzMarkedPos--;
        zzStartRead = textStart;
        yybegin(CONTENTS);
        return DOC_CODE;
    }
    [^] {}
}

<CODE3> {
    "```" {
        zzMarkedPos -= 3;
        zzStartRead = textStart;
        yybegin(CLOSE_CODE3);
        return DOC_CODE_LINE;
    }
    {CODE_NEW_LINE} {
        zzMarkedPos = zzStartRead;
        zzStartRead = textStart;
        yybegin(CLOSE_CODE3);
        return DOC_CODE_LINE;
      }
    [^] {}
}

<TEXT,CODE1,CODE2,CODE3,REFERENCE_TEXT> {
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

<CLOSE_CODE1>"`" {
    yybegin(CONTENTS);
    return DOC_INLINE_CODE_BORDER;
}

<CLOSE_CODE2>"``" {
    yybegin(CONTENTS);
    return DOC_INLINE_CODE_BORDER;
}

<CLOSE_CODE3> {
    "```" {
        yybegin(CONTENTS);
        return DOC_CODE_BLOCK_BORDER;
    }
    {NEW_LINE_HYPHEN} {
        if (checkNextToLast()) {
          yybegin(CONTENTS);
        } else {
          yybegin(CODE3);
        }
        return WHITE_SPACE;
    }
    {CODE_NEW_LINE} {
        yybegin(CODE3);
        return WHITE_SPACE;
    }
}

[^] { return BAD_CHARACTER; }
