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
        |\class <decl>C</decl> (<decl>x</decl> : <id>Nat</id> -> <id>Nat</id>) (<decl>y</decl> : <id>Nat</id>) {
        | | <decl>Z</decl> : \Type
        | \field <decl>P</decl> : \Prop
        | \property <decl>p</decl> : <id>P</id>
        |}
        |
        |\func <decl>f</decl> (<class_param>c</class_param> <class_param>d</class_param> : <id>C</id>) (<id>a</id> <id>b</id> _ : <id>Nat</id>) => <id>C</id> { | <id>x</id> => \lam <id>n</id> => <id>n</id> | <id>y</id> => <id>a</id> <long>Nat.</long><op>+</op> <id>b</id> }
        |
        |\func <decl>tuple</decl> => (<id>one</id>, <long>one.</long><id>two</id>, <long>one.two.</long><id>three</id>, 0 <op>`f`</op> 1, \Set0, _, <meta>meta</meta>)
        |
        |-- comment
        |
        |{- block
        |   comment
        |-}
        |
        |-- | documentation
        |\data <decl>D</decl> | <decl>con1</decl> | <decl>con2</decl>
        |
        |\func <decl>bad</decl> => й
        """.trimMargin()

    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        Pair("decl", ArendHighlightingColors.DECLARATION.textAttributesKey),
        Pair("long", ArendHighlightingColors.LONG_NAME.textAttributesKey),
        Pair("op",   ArendHighlightingColors.OPERATORS.textAttributesKey),
        Pair("id",   ArendHighlightingColors.IDENTIFIER.textAttributesKey),
        Pair("meta", ArendHighlightingColors.META_RESOLVER.textAttributesKey),
        Pair("class_param", ArendHighlightingColors.CLASS_PARAMETER.textAttributesKey)
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
