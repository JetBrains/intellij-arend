package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveLevelQuickFixTest : QuickFixTestBase() {

    fun testRemoveLevel() = typedQuickFixTest(
        ArendBundle.message("arend.expression.removeLevel"), """
        \data Empty

        \func test : \level{-caret-} Empty (Path.inProp {Empty}) => {?}
    """, """
        \data Empty

        \func test : Empty => {?}
    """)
}
