package org.arend.highlight

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.ide.highlighter.JavaHighlightingColors as Java

enum class ArendHighlightingColors(humanName: String, default: TextAttributesKey? = null) {
    NUMBER("Numbers", Default.NUMBER),
    KEYWORD("Keywords", Default.KEYWORD),
    UNIVERSE("Universes", Default.KEYWORD),
    IMPLICIT("Implicit", Default.INSTANCE_FIELD),
    CLASS_PARAMETER("Class parameters", Java.STATIC_FIELD_ATTRIBUTES),

    IDENTIFIER("References//Identifiers", Default.IDENTIFIER),
    LONG_NAME("References//Long names", Default.CONSTANT),
    LEMMA("References//Lemmas"),
    PROPERTY("References//Properties", LEMMA.textAttributesKey),

    DECLARATION("Declarations//Top level", Default.FUNCTION_DECLARATION),
    DECLARATION_LEMMA("Declarations//Lemmas", DECLARATION.textAttributesKey),
    DECLARATION_CON("Declarations//Constructors", IDENTIFIER.textAttributesKey),
    DECLARATION_FIELD("Declarations//Fields", DECLARATION_CON.textAttributesKey),
    DECLARATION_PROP("Declarations//Properties", PROPERTY.textAttributesKey),

    OPERATORS("Operators", Default.COMMA),
    DOT("Separators//Dot", Default.COMMA),
    COMMA("Separators//Comma", Default.COMMA),
    PIPE("Separators//Pipe", Default.COMMA),
    COLON("Separators//Colon", Default.COMMA),
    ARROW("Arrows", Default.COMMA),

    BRACES("Braces", Default.BRACES),
    PARENTHESIS("Parenthesis", Default.PARENTHESES),

    BLOCK_COMMENT("Comments//Block comment", Default.BLOCK_COMMENT),
    LINE_COMMENT("Comments//Line comment", Default.LINE_COMMENT),
    DOC_COMMENT("Comments//Documentation", Default.DOC_COMMENT),

    BAD_CHARACTER("Bad characters", HighlighterColors.BAD_CHARACTER);

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.arend.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}
