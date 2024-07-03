package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class RedundantParensInspectionTest : QuickFixTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RedundantParensInspection::class.java)
    }

    fun testNeverNeedsParens() = doWeakWarningsCheck(myFixture, """
       \open Nat (+)

       \func f2 {A : \Type} {B : \Type} (a : A) (b : B) => {?}

       \func test0 : \Sigma => ()

       \func test1 => 1 + ${rp("(2)")}
       \func test2 => ${rp("(2)")}
       \func test3 => f2 {Nat} ${rp("(1)")} 2

       \data Empty
       \axiom prop-pi {A : \Prop} (x y : A) : x = y
       \lemma p => prop-pi {Empty}
       \func test4 : $LEVEL ${rp("(Empty)")} ${rp("(p)")} => {?}
       \func test5 : $LEVEL Empty (prop-pi {Empty}) => {?}

       \class Unit
       \func test6 => f2 (\new Unit) 1

       \class Pair (x y : Nat)
       \func test7 => f2 (Pair { | x => 1 }) 1

       \func test8 => f2 (\Set 0) 1

       \func test9 => f2 (Path \levels 0 0) 1
       \func test10 {A : \Type \lp \lh} => f2 (Path \lp \lh) 1

       \func test11 => f2 (\Sigma) 1
       \func test12 => f2 (\Pi (n : Nat) -> Nat) 1
       \func test13 (e : Empty) => f2 {Empty} (\case e \with {}) 1

       \func test14 => f2 (+) 1 
    """)

    fun testNewExpression() = doWeakWarningsCheck(myFixture,"""
       \class Pair (x y : Nat)

       \func test1 => \new ${rp("(Pair 1 2)")}
       \func test2 => ${rp("(Pair 1)")} { | y => 2 }

       \func pair (n : Nat) => (n, n)
       \func test3 => (pair 0).1 
    """)

    fun testReturnExpression() = doWeakWarningsCheck(myFixture,"""
       \data Empty

       \func test1 : ${rp("(1 = 1)")} => idp
       \axiom prop-pi {A : \Prop} (x y : A) : x = y
       \func test2 : Empty $LEVEL ${rp("(prop-pi {Empty})")} => {?} 
    """)

    fun testParameterType() = doWeakWarningsCheck(myFixture,"""
       \func test1 {a : ${rp("(0 = 0)")}} (b : ${rp("(0 = 0)")}) => 1

       \record A (f : ${rp("(0 = 0)")} -> Nat)
       \record test2 (a : ${rp("(0 = 0)")}) \extends A {
         | b (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) : Nat
         \field c (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) : Nat
         \override f (a : ${rp("(0 = 0)")}) : Nat
         \default f (a : ${rp("(0 = 0)")}) : Nat => 1
       }

       \class test3 (a : ${rp("(0 = 0)")})
         | test4 (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) : Nat

       \data test5 (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")})
         | test6 (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) 
    """)

    fun testBodyAndClauseAndCoClause() = doWeakWarningsCheck(myFixture,"""
       \open Nat (+)

       \class Pair (x y : Nat)

       \func test1 => ${rp("(1 = 1)")}
       \func test2 (n : Nat) : Nat
         | n => ${rp("(0 + 0)")}

       \meta test3 => ${rp("(1 = 1)")}

       \instance test4 : Pair => ${rp("(\\new Pair { | x => 0 | y => 0 })")}
       \instance test5 : Pair
         | x => ${rp("(0 + 0)")}
         | y : Nat => ${rp("(0 + 0)")}

       \record B (n : Nat)
       \record test6 \extends B
         | n => ${rp("(1 + 2)")}

       \func test7 => \new Pair 0 { | y => ${rp("(0 + 0)")} } 
    """)

    fun testClausePattern() = doWeakWarningsCheck(myFixture,"""
       \func test1 (p : 1 = 1) : Nat
         | p : ${rp("(1 = 1)")} => 1

       \func test2 (p : 1 = 1) : Nat
         | p \as p' : ${rp("(1 = 1)")} => 1 
    """)

    fun testArrowExpression() = doWeakWarningsCheck(myFixture,"""
       \func test1 => (Nat -> Nat) -> Nat
       -- False negatives. But removing parens might hurt readability, especially in the second case.
       \func test2 => ${rp("(0 = 1)")} -> Nat
       \func test3 => (\Sigma Nat Nat) -> Nat 
    """)

    fun testSigmaExpression() = doWeakWarningsCheck(myFixture,"""
       \func test1 => \Sigma Nat (Nat -> Nat) -- Without parens this Sigma turns into Arrow
       \func test2 => \Sigma (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) 
    """)

    fun testPiExpression() = doWeakWarningsCheck(myFixture,"""
       \func test1 => \Pi (n : Nat) -> ${rp("(Nat -> Nat)")}
       \func test2 => \Pi (n : Nat) -> ${rp("(0 = 0)")}
       \func test3 => \Pi (a : ${rp("(0 = 0)")}) (${rp("(0 = 0)")}) -> Nat 
    """)

    fun testLambdaExpression() = doWeakWarningsCheck(myFixture,"""
       \func test1 => \lam (x : Nat) => ${rp("(0 = 0)")}
       \func test2 => \lam {a : ${rp("(0 = 0)")}} (b : ${rp("(0 = 0)")}) => 1 
    """)

    fun testLetExpression() = doWeakWarningsCheck(myFixture,"""
       \func test1 => \let N => Nat \in ${rp("(N -> N)")}
       \func test2 => \let p => ${rp("(0 = 0)")} \in p
       \func test3 => \let p : ${rp("(1 = 1)")} => idp \in p 
    """)

    fun testTupleExpression() = doWeakWarningsCheck(myFixture,"""
       \open Nat (+)

       \func test1 => (${rp("(1 + 2)")} : Nat, ${rp("(3 + 4)")})

       \func f {A : \Type} => 1
       \func test2 => f {${rp("(1 = 1)")} : ${rp("(\\Type 0)")}}

       \func test3 : \Sigma (0 = 1 -> Nat) Nat => ((\case __), 2)
       \func test4 : \Sigma (0 = 1 -> Nat) Nat => (${rp("(\\case __ \\with {})")}, 2)
       \func test5 : \Sigma (0 = 1 -> Nat) Nat => (${rp("(\\case __ \\return Nat)")}, 2) 
    """)

    fun testMetaDefCallWithClauses() = doWeakWarningsCheck(myFixture,"""
       \func f2 {A : \Type} {B : \Type} (a : A) (b : B) => {?}

       \meta mcases => 1
       \func test15 => f2 (mcases <error descr="Clauses are not allowed here">\with {}</error>) 1 
    """)

    fun testApplicationUsedAsBinOpArgument() = doWeakWarningsCheck(myFixture,"""
       \open Nat (+)

       \func test1 => ${rp("(f 1 2)")} + 3
       \func test2 => 3 + ${rp("(f 1 2)")}
       \func test3 => 3 `f2` ${rp("(f 1 2)")}
       \func test5 => 3 `f2` (pair 0).1
       \func test4 => 3 `f2` (\new Unit)

       \func pair (n : Nat) => (n, n)
       \record Unit

       \func f (_ _ : Nat) : Nat => 0
       \func f2 {A : \Type} {B : \Type} (_ : A) (_ : B) => {?}

       \func test6 => ${rp("(suc 0)")} + 1 + 2
       \func test7 => 0 + ${rp("(suc 1)")} + 2
       \func test8 => 0 + 1 + ${rp("(suc 2)")}

       \func test9 => suc (suc 0) + 1
       \func test10 => 0 + suc (suc 1)

       \func test12 => suc (suc 0) + 1 + 2
       \func test13 => 0 + suc (suc 1) + 2
       \func test14 => 0 + 1 + suc (suc 2)

       \func test15 {A : \Type} (l : Array A) (a : A) => a :: (:: \levels \lp \lh a l)
       \func test16 {A : \Type} (l : Array A) (a : A) => a :: (:: \lp \lh a l)

       \func test17 => + (suc 0) (suc 1)
       \func test18 => +(suc 0) (suc 1)

       \func test19 => (`+ 2) 1 = 3

       \func \infixl 5 +++ (a b : Nat -> Nat) => a 1 + b 1

       -- The first parens is actually redundant, this is false negative.
       \func test20 => (+ 1) +++ (+ 3) 
    """, true)

    fun `test fix for atomic expression in function body`() = doTypedQuickFixTest("""
      \func test => (2){-caret-}
    """, """
      \func test => 2
    """)

    fun `test fix for composite expression in return type`() = doTypedQuickFixTest("""
      \func test : (0 = 0){-caret-} => idp
    """, """
      \func test : 0 = 0 => idp
    """)

    fun `test issues`() = doWeakWarningsCheck(myFixture,"""
       \module Issue302 \where
         \func foo (F : Nat -> \Type) => ${rp("(F 0)")} -> ${rp("(F 0)")} 
       \module Issue326 \where {
         \func bar {x : \Sigma Nat Nat} => x.1
         \func foo => bar {${rp("(1,2)")}}
       }
       \module Issue339 \where {
         \func test => \Sigma ${rp("(Nat)")} ${rp("(\\Type)")} (Nat -> Nat) (\Type 1) ${rp("(\\Set)")} ${rp("(\\Prop)")}
       }
       \module Issue350 \where {
       \func test (f : Nat -> Nat) : Nat => \case ${rp("(f 0)")} \with {
         | 0 => 0
         | suc n => n
         }
       }              
    """)

    fun testReturnExpr() = doWeakWarningsCheck(myFixture,"""       
         \data Bool | true | false
         
         \func lol (a : Bool) : Bool -> Bool => \case a \return (Bool -> Bool) \with { 
           | true => \lam y => true
           | false => \lam z => true
         } 
    """)

    fun test375() = doWeakWarningsCheck(myFixture,"""
       \module Issue375 \where {
         \class Ring (E : \Set)
           | \infixl 6 + : E -> E -> E
           | \infixl 7 * : E -> E -> E

         \func test {R : Ring} (x y z : R) (p : (x R.+ y) * z = x) => p
       } 
    """)

    fun test360() = doTypedQuickFixTest("""
       \func foo : Nat -> Nat => {?}
       \func bar => foo(1{-caret-})
    """, """
       \func foo : Nat -> Nat => {?}
       \func bar => foo 1
    """)

    fun test302() = doTypedQuickFixTest("""
       \func test : Nat -> (Nat -> Nat){-caret-} => {?} 
    """, """
       \func test : Nat -> Nat -> Nat => {?} 
    """)

    fun test339_1() = doTypedQuickFixTest("""       
        \func test => \Sigma (Nat){-caret-} (Nat -> Nat)
    """, """
        \func test => \Sigma Nat (Nat -> Nat)
    """)

    fun test339_2() = doTypedQuickFixTest("""       
        \func test => \Sigma (\Type){-caret-} (Nat -> Nat)
    """, """
        \func test => \Sigma \Type (Nat -> Nat)
    """)

    fun test339_3() = doTypedQuickFixTest("""       
        \func test => \Sigma (\Set){-caret-} (Nat -> Nat)
    """, """
        \func test => \Sigma \Set (Nat -> Nat)
    """)

    fun test339_4() = doTypedQuickFixTest("""       
        \func test => \Sigma (\Prop){-caret-} (Nat -> Nat)
    """, """
        \func test => \Sigma \Prop (Nat -> Nat)
    """)

    fun testArendMaybeAtomLevelExprs() = doTypedQuickFixTest("""       
        \func a : 1 = 1 => idp \levels _ (0){-caret-}
    """, """
        \func a : 1 = 1 => idp \levels _ 0
    """)

    fun `test a tuple with a tuple with case expression`() = doWeakWarningsCheck(myFixture,"""
       \data Empty

       \func foo : \Sigma (Empty -> Nat) Nat => (\lam e => (\case e), 0) 
    """)

    fun `test a tuple with a tuple with only one case expression`() = doTypedQuickFixTest("""
       \data Empty

       \func foo => (\lam e => (\case e)){-caret-}
    """, """
       \data Empty

       \func foo => \lam e => (\case e)
    """)

    private fun doTypedQuickFixTest(before: String, after: String) =
            typedQuickFixTest(ArendBundle.message("arend.unwrap.parentheses.fix"), before, after)

    companion object {

        fun rp(text: String): String = "<weak_warning descr=\"Redundant parentheses\">$text</weak_warning>"

        const val LEVEL = "<weak_warning descr=\"\\level is ignored\">\\level</weak_warning>"
    }
}