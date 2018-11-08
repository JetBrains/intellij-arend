package org.arend.formatting

class ArendReformatTest : ArendFormatterTestBase() {
    fun testWrapAfterComment() = checkReformat("\\func lol --Lol\n => 1",
            "\\func lol --Lol\n  => 1")
}