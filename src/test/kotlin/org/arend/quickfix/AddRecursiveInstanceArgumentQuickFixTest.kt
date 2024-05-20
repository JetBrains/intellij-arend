package org.arend.quickfix

import org.arend.util.ArendBundle

class AddRecursiveInstanceArgumentQuickFixTest : QuickFixTestBase() {

    fun testAddRecursiveInstanceArgument1() = typedQuickFixTest(
        ArendBundle.message("arend.instance.addLocalRecursiveInstance"), """
        \class B
        \class C
        \func f {b : B} {c : C} => 0
        \class D {c : C} (X : \Type) \extends B, C
          | g : f{-caret-} = f
    """, """
        \class B
        \class C
        \func f {b : B} {c : C} => 0
        \class D {b : B} {c : C} (X : \Type) \extends B, C
          | g : f = f
    """)

    fun testAddRecursiveInstanceArgument2() = typedQuickFixTest(
        ArendBundle.message("arend.instance.addLocalRecursiveInstance"), """
        \class b
        \class C
        \func f {b : b} {c : C} => 0
        \class D {c : C} (X : \Type) \extends b, C
          | g : f{-caret-} = f
    """, """
        \class b
        \class C
        \func f {b : b} {c : C} => 0
        \class D {b0 : b} {c : C} (X : \Type) \extends b, C
          | g : f = f
    """)
}
