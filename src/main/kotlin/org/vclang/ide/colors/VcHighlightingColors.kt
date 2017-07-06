package org.vclang.ide.colors


import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor

enum class VcHighlightingColors(humanName: String, default: TextAttributesKey) {
    IDENTIFIER("Identifier", Default.IDENTIFIER),
    NUMBER("Number", Default.NUMBER),
    KEYWORD("Keyword", Default.KEYWORD),
    UNIVERSE("Universe", Default.LABEL),
    HOLE("Hole", Default.INSTANCE_FIELD),

    OPERATORS("Operator sign", Default.OPERATION_SIGN),
    DOT("Dot", Default.DOT),
    COMMA("Comma", Default.COMMA),
    GRAVE("Grave", Default.COMMA),
    COLON("Colon", Default.DOT),
    ARROW("Arrow", Default.COMMA),

    BRACES("Braces", Default.BRACES),
    PARENTHESIS("Parenthesis", Default.PARENTHESES),

    BLOCK_COMMENT("Block comment", Default.BLOCK_COMMENT),
    LINE_COMMENT("Line comment", Default.LINE_COMMENT),

    BAD_CHARACTER("Bad character", HighlighterColors.BAD_CHARACTER);

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.vclang.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}
