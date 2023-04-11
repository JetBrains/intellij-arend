package org.arend.quickfix

import org.arend.util.ArendBundle

class SquashedDataQuickFixTest: QuickFixTestBase() {

    fun testSquashedWithFuncLevelError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \truncated \data D (A : \Type) : \Prop | con A
        \func id {A : \Type} (p : \Pi (x y : A) -> x = y) => A
          \where \use \level levelProp {A : \Type} (p : \Pi (x y : A) -> x = y) (x y : id p) : x = y => p x y
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) : id p \elim d | con a => a{-caret-}
    """, """
        \truncated \data D (A : \Type) : \Prop | con A
        \func id {A : \Type} (p : \Pi (x y : A) -> x = y) => A
          \where \use \level levelProp {A : \Type} (p : \Pi (x y : A) -> x = y) (x y : id p) : x = y => p x y
        \sfunc f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) : id p \elim d | con a => a
    """)

    fun testSquashedByTruncationCaseError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \truncated \data D (A : \Type) : \Prop | con A
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) => \case d \return \level A p \with { | con a{-caret-} => a }
    """, """
        \truncated \data D (A : \Type) : \Prop | con A
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) => \scase d \return \level A p \with {| con a => a }
    """)

    fun testSquashedByTruncationError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \truncated \data D (A : \Type) : \Prop | con A
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) : \level A p \elim d | con a => a{-caret-}
    """, """
        \truncated \data D (A : \Type) : \Prop | con A
        \sfunc f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) : \level A p \elim d | con a => a
    """)

    fun testSquashedByUseError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) : A \elim d | con a => a{-caret-}
    """, """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \sfunc f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) : A \elim d | con a => a
    """)

    fun testSquashedByUseCaseWithLevelError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) => \case d \return \level A p \with { | con a{-caret-} => a }
    """, """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) => \scase d \return \level A p \with {| con a => a }
    """)

    fun testSquashedWithFuncLevelCaseError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \truncated \data D (A : \Type) : \Prop | con A
        \func id {A : \Type} (p : \Pi (x y : A) -> x = y) => A
          \where \use \level levelProp {A : \Type} (p : \Pi (x y : A) -> x = y) (x y : id p) : x = y => p x y
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) => \case d \return id p \with { | con a{-caret-} => a }
    """, """
        \truncated \data D (A : \Type) : \Prop | con A
        \func id {A : \Type} (p : \Pi (x y : A) -> x = y) => A
          \where \use \level levelProp {A : \Type} (p : \Pi (x y : A) -> x = y) (x y : id p) : x = y => p x y
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A) => \scase d \return id p \with {| con a => a }
    """)

    fun testSquashedByUseCaseError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) => \case d \return A \with { | con a{-caret-} => a }
    """, """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) => \scase d \return A \with {| con a => a }
    """)

    fun testSquashedByUseWithLevelError() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \func f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) : \level A p \elim d | con a => a{-caret-}
    """, """
        \data D (A : \Type) (p : \Pi (x y : A) -> x = y) | con A
          \where \use \level levelProp {A : \Type} {p : \Pi (x y : A) -> x = y} (d1 d2 : D A p) : d1 = d2 \elim d1, d2
            | con a1, con a2 => path (\lam i => con (p a1 a2 @ i))
        \sfunc f {A : \Type} (p : \Pi (x y : A) -> x = y) (d : D A p) : \level A p \elim d | con a => a
    """)

    fun testFuncLevelTest() = typedQuickFixTest(
        ArendBundle.message("arend.squashedData.changeKeyword"), """
        \truncated \data Trunc (A : \Type) : \Prop | in A
        \func test {A : \Type} (p : \Pi (a a' : A) -> a = a') (t : Trunc A) : \level A p => \case t \with { | in a{-caret-} => a }
    """, """
        \truncated \data Trunc (A : \Type) : \Prop | in A
        \func test {A : \Type} (p : \Pi (a a' : A) -> a = a') (t : Trunc A) : \level A p => \scase t \return {?} \level {?} \with {| in a => a }
    """)
}
