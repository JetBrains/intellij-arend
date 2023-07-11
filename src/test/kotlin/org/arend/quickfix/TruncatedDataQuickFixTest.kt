package org.arend.quickfix

import org.arend.util.ArendBundle

class TruncatedDataQuickFixTest: QuickFixTestBase() {

    fun testFuncLevelTest() = typedQuickFixTest(
        ArendBundle.message("arend.truncatedData.changeKeyword"), """
        \truncated \data Trunc (A : \Type) : \Prop | in A
        \func test {A : \Type} (p : \Pi (a a' : A) -> a = a') (t : Trunc A) : \level A p => \case t \with { | in a{-caret-} => a }
    """, """
        \truncated \data Trunc (A : \Type) : \Prop | in A
        \lemma test {A : \Type} (p : \Pi (a a' : A) -> a = a') (t : Trunc A) : \level A p => \case t \with {| in a => a }
    """)
}
