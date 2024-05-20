package org.arend.quickfix

import org.arend.util.ArendBundle

class AddTruncatedUniverseQuickFixTest : QuickFixTestBase() {

    fun testAddTruncatedUniverse1() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.universe.add"), """
        \truncated{-caret-} \data D
          | con1
          | con2
          | con3
    """, """
        \truncated \data D : \Prop
          | con1
          | con2
          | con3
    """)

    fun testAddTruncatedUniverse2() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.universe.add"), """
        \import Algebra.Group

        \truncated{-caret-} \data K1 (G : Group)
          | base
          | loop G : base = base
          | relation (g g' : G) (i : I) (j : I) \elim i, j {
            | left, j => base
            | right, j => loop g' j
            | i, left => loop g i
            | i, right => loop (g * g') i
          }
    """, """
        \import Algebra.Group

        \truncated \data K1 (G : Group) : \0-Type
          | base
          | loop G : base = base
          | relation (g g' : G) (i : I) (j : I) \elim i, j {
            | left, j => base
            | right, j => loop g' j
            | i, left => loop g i
            | i, right => loop (g * g') i
          }
    """)

    fun testAddTruncatedUniverse3() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.universe.add"), """
        \truncated{-caret-} \data D
    """, """
        \truncated \data D : \Prop
    """)
}
