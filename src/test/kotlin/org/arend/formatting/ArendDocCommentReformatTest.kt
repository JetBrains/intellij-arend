package org.arend.formatting

class ArendDocCommentReformatTest : ArendFormatterTestBase() {
    fun testDocCommentReformat1() = checkReformat(
        "{- |\n" +
                " - * item1\n" +
                " -  1. item12\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -   1. item12\n" +
                " - -}")

    fun testDocCommentReformat2() = checkReformat(
        "{- |\n" +
                " - * item1\n" +
                " -  1. item12\n" +
                " -    + item123\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -   1. item12\n" +
                " -     + item123\n" +
                " - -}")

    fun testDocCommentReformat3() = checkReformat(
        "{- |\n" +
                " - * item1\n" +
                " -  1. item12\n" +
                " -    + item123\n" +
                " -   2. item22\n" +
                " -    3. item221\n" +
                " - -}",
        "{- |\n" +
                " - * item1\n" +
                " -   1. item12\n" +
                " -     + item123\n" +
                " -   2. item22\n" +
                " -     3. item221\n" +
                " - -}")
}
