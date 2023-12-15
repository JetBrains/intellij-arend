package org.arend.highlight

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.ide.highlighter.JavaHighlightingColors as Java

enum class ArendHighlightingColors(humanName: String, default: TextAttributesKey) {
    IDENTIFIER("Identifier", Default.IDENTIFIER),
    META_RESOLVER("Meta resolvers", Default.STATIC_METHOD),
    CONSTRUCTOR_PATTERN("Constructor in pattern", Default.INSTANCE_FIELD),
    NUMBER("Literals//Number", Default.NUMBER),
    STRING("Literals//String", Default.STRING),
    KEYWORD("Keyword", Default.KEYWORD),
    UNIVERSE("Universe", Default.KEYWORD),
    IMPLICIT("Implicit", Default.INSTANCE_FIELD),
    DECLARATION("Declaration", Default.FUNCTION_DECLARATION),
    CLASS_PARAMETER("Class parameter", Java.STATIC_FIELD_ATTRIBUTES),

    OPERATORS("Operator", Default.COMMA),
    DOT("Separators//Dot", Default.COMMA),
    COMMA("Separators//Comma", Default.COMMA),
    PIPE("Separators//Pipe", Default.COMMA),
    COLON("Separators//Colon", Default.COMMA),
    ARROW("Arrow", Default.COMMA),

    BRACES("Braces", Default.BRACES),
    PARENTHESIS("Parenthesis", Default.PARENTHESES),

    BLOCK_COMMENT("Comments//Block comment", Default.BLOCK_COMMENT),
    LINE_COMMENT("Comments//Line comment", Default.LINE_COMMENT),
    DOC_COMMENT("Comments//Documentation", Default.DOC_COMMENT),
    LONG_NAME("Long name", Default.CONSTANT),

    BAD_CHARACTER("Bad character", HighlighterColors.BAD_CHARACTER),

    REFERENCE("Reference", Default.HIGHLIGHTED_REFERENCE),
    GOALS("Goals", Default.COMMA),
    DOC_TOKENS("Document tokens", Default.DOC_COMMENT_MARKUP)
    ;

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.arend.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)

    companion object {
        val AREND_COLORS = values().toList()

        val colorsByAttributesKey = values().associateBy { it.textAttributesKey }
    }
}
