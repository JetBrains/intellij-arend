package org.arend.formatting

class ArendDocCommentNewLineTest : ArendFormatterTestBase() {
    fun testDocCommentNewLine1() = checkNewLine(
        "{- |\n" +
                " - * item1{-caret-}\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " - * {-caret-}\n" +
                " - -}")

    fun testDocCommentNewLine2() = checkNewLine(
        "{- |\n" +
                " - * item1\n" +
                " -   * item12{-caret-}\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -   * item12\n" +
                " -   * {-caret-}\n" +
                " - -}")

    fun testDocCommentNewLine3() = checkNewLine(
        "{- |\n" +
                " - * item1\n" +
                " -   1. item12{-caret-}\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -   1. item12\n" +
                " -   2. {-caret-}\n" +
                " - -}")

    fun testDocCommentNewLine4() = checkNewLine(
        "{- |\n" +
                " - * item1\n" +
                " -  1. item12{-caret-}\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -  1. item12\n" +
                " -   2. {-caret-}\n" +
                " - -}")

    fun testDocCommentNewLine5() = checkNewLine(
        "{- |\n" +
                " - * item1{-caret-}\n" +
                " -  1. item12\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " - * {-caret-}\n" +
                " -  1. item12\n" +
                " - -}")

    fun testDocCommentNewLine6() = checkNewLine(
        "{- |{-caret-}\n" +
                "\\class Empty",
        "{- |\n" +
                " - {-caret-}\n" +
                " -}\n" +
                "\\class Empty")

    fun testDocCommentNewLine7() = checkNewLine(
        "{- |{-caret-}\n" +
                " -}\n" +
                "\\class Empty",
        "{- |\n" +
                " - {-caret-}\n" +
                " -}\n" +
                "\\class Empty")

    fun testDocCommentNewLine8() = checkNewLine(
        "{- |{-caret-}\n" +
                " - \n" +
                " -}\n" +
                "\\class Empty",
        "{- |\n" +
                " - {-caret-}\n" +
                " - \n" +
                " -}\n" +
                "\\class Empty")
}
