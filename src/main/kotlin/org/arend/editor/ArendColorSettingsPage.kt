package org.arend.editor

import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.arend.ArendIcons
import org.arend.highlight.ArendHighlightingColors
import org.arend.highlight.ArendSyntaxHighlighter

class ArendColorSettingsPage : ColorSettingsPage {

    override fun getIcon() = ArendIcons.AREND

    override fun getHighlighter() = ArendSyntaxHighlighter()

    override fun getDemoText() =
        """
        |\import <id>Logic</id>.<id>Classical</id>
        |
        |\func <decl>one</decl> => 1
        |  \where <decl>two</decl> => 2
        |    \where <decl>three</decl> => 3
        |   
        |\class <decl>C</decl> (<field_decl>x</field_decl> : <id>Nat</id> -> <id>Nat</id>) (<field_decl>y</field_decl> : <id>Nat</id>) {
        | | <field_decl>Z</field_decl> : \Type
        | | <prop_decl>property</prop_decl> : 0 = 0
        | \field <field_decl>P</field_decl> : \Prop
        | \property <prop_decl>p</prop_decl> : <id>P</id>
        |}
        |
        |\func <decl>f</decl> (<class_param>c</class_param> <class_param>d</class_param> : <id>C</id>) (<id>a</id> <id>b</id> _ : <id>Nat</id>) => <id>C</id> { | <id>x</id> => \lam <id>n</id> => <id>n</id> | <id>y</id> => <id>a</id> <long>Nat.</long><op>+</op> <id>b</id> }
        |
        |\func <decl>tuple</decl> => (<id>one</id>, <long>one.</long><id>two</id>, <long>one.two.</long><id>three</id>, 0 <op>`f`</op> 1, \Set0, _)
        |
        |\lemma <lem_decl>some_lemma</lem_decl> (<class_param>c</class_param> : <id>C</id>) => <long>c.</long><prop>property</prop>
        |
        |\lemma <lem_decl>another_lemma</lem_decl> => <lem>some_lemma</lem>
        |
        |-- comment
        |
        |{- block
        |   comment
        |-}
        |
        |-- | documentation
        |\data <decl>D</decl> | <con_decl>con1</con_decl> | <con_decl>con2</con_decl>
        |
        |\func <decl>bad</decl> => й
        """.trimMargin()

    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        Pair("decl",        ArendHighlightingColors.DECLARATION.textAttributesKey),
        Pair("long",        ArendHighlightingColors.LONG_NAME.textAttributesKey),
        Pair("op",          ArendHighlightingColors.OPERATORS.textAttributesKey),
        Pair("id",          ArendHighlightingColors.IDENTIFIER.textAttributesKey),
        Pair("class_param", ArendHighlightingColors.CLASS_PARAMETER.textAttributesKey),
        Pair("con_decl",    ArendHighlightingColors.DECLARATION_CON.textAttributesKey),
        Pair("lem",         ArendHighlightingColors.LEMMA.textAttributesKey),
        Pair("lem_decl",    ArendHighlightingColors.DECLARATION_LEMMA.textAttributesKey),
        Pair("field_decl",  ArendHighlightingColors.DECLARATION_FIELD.textAttributesKey),
        Pair("prop",        ArendHighlightingColors.PROPERTY.textAttributesKey),
        Pair("prop_decl",   ArendHighlightingColors.DECLARATION_PROP.textAttributesKey)
    )

    override fun getAttributeDescriptors() = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName() = "Arend"

    companion object {
        private val DESCRIPTORS = ArendHighlightingColors.values()
            .map { it.attributesDescriptor }
            .toTypedArray()
    }
}
