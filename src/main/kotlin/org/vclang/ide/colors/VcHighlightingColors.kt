package org.vclang.ide.colors

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

enum class VcHighlightingColors(humanName: String, default: TextAttributesKey) {
    IDENTIFIER("Identifier", Default.IDENTIFIER),
    NUMBER("Number", Default.NUMBER),
    KEYWORD("Keyword", Default.KEYWORD),
    UNIVERSE("Universe", Default.LABEL),
    IMPLICIT("Implicit", Default.INSTANCE_FIELD),
    DECLARATION("Declaration", Default.FUNCTION_DECLARATION),

    OPERATORS("Operator sign", Default.COMMA),
    DOT("Dot", Default.COMMA),
    COMMA("Comma", Default.COMMA),
    GRAVE("Grave", Default.COMMA),
    COLON("Colon", Default.COMMA),
    ARROW("Arrow", Default.COMMA),

    BRACES("Braces", Default.BRACES),
    PARENTHESIS("Parenthesis", Default.PARENTHESES),

    BLOCK_COMMENT("Block comment", Default.BLOCK_COMMENT),
    LINE_COMMENT("Line comment", Default.LINE_COMMENT),

    BAD_CHARACTER("Bad character", HighlighterColors.BAD_CHARACTER);

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.vclang.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}
