package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveNoClassifyingKeywordQuickFixTest: QuickFixTestBase() {

    fun testRemoveNoClassifyingKeyword() = typedQuickFixTest(
        ArendBundle.message("arend.noclassifying.remove"), """
            \class D{-caret-} \noclassifying
    """, """
            \class D
    """)
}
