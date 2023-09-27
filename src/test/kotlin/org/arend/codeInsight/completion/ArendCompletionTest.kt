package org.arend.codeInsight.completion


class ArendCompletionTest : ArendCompletionTestBase() {

    /* field completion */
    fun `test local variable`() =
        checkCompletionVariants(
            "\\class C { | f : Nat | g : Nat }\n" +
            "\\func test (c : C) => c.{-caret-}",
            listOf("f", "g"))

    fun `test iterated`() =
        checkCompletionVariants(
            "\\class A { | f : Nat }\n" +
            "\\class B { | g : A }\n" +
            "\\func test (b : B) => b.g.{-caret-}",
            listOf("f"))

    fun `test field parameter`() =
        checkCompletionVariants(
            "\\class A (g : Nat) { | f : Nat }\n" +
            "\\func test (a : A) => a.{-caret-}",
            listOf("g", "f"))

    fun `test function`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func : A\n" +
            "\\func test => func.{-caret-}",
            listOf("f", "g"))

    fun `test instance`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\instance inst : A\n" +
            "\\func test => inst.{-caret-}",
            listOf("f", "g"))

    fun `test extended`() =
        checkCompletionVariants(
            "\\class A { | f : Nat }\n" +
            "\\class B \\extends A { | g : Nat }\n" +
            "\\func test (b : B) => b.{-caret-}",
            listOf("f", "g"))

    fun `test function with parameters`() =
        checkNoCompletion(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func (x : Nat) : A\n" +
            "\\func test => func.{-caret-}")

    fun `test function with implicit parameters`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func {x y : Nat} {z : Nat -> Nat} : A\n" +
            "\\func test => func.{-caret-}",
            listOf("f", "g"))


    fun `test complex`() =
        checkCompletionVariants(
            "\\class A (f : Nat) { | g : Nat }\n" +
            "\\class B \\extends A { | h : Nat }\n" +
            "\\class C { | a : A | b : B }\n" +
            "\\func test (c : C) => c.b.{-caret-}",
            listOf("f", "g", "h"))

    fun `test function reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B => A\n" +
            "\\func test (a : B) => a.{-caret-}",
            listOf("f", "g"))

    fun `test iterated function reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func C => B\n" +
            "\\func B => A\n" +
            "\\func test (a : C) => a.{-caret-}",
            listOf("f", "g"))

    fun `test recursive function reference`() =
        checkNoCompletion(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func C => B\n" +
            "\\func B => C\n" +
            "\\func test (a : C) => a.{-caret-}")

    fun `test function with parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B (x : Nat) => A\n" +
            "\\func test (a : B 0) => a.{-caret-}",
            listOf("f", "g"))

    fun `test function with no parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B (x : Nat) => A\n" +
            "\\func test (a : B) => a.{-caret-}",
            listOf("f", "g"))

    fun `test infix function with parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func \\infixr 3 + (x y : Nat) => A\n" +
            "\\func test (a : 0 + 1) => a.{-caret-}",
            listOf("f", "g"))

    fun `test pattern`() =
        checkCompletionVariants(
            "\\class A (f g : Nat)\n" +
            "\\data D | con A\n" +
            "\\func test (d : D) : Nat | con (a : A) => a.{-caret-}",
            listOf("f", "g"))

    /* private */

    fun `test private`() =
        checkCompletionVariants("""
           \module M \where \private {
             \func foo (n : Nat) => n
             \func bar => foo 0
           }

           \func lol => M.{-caret-}
        """, listOf())

    fun `test private 2`() =
        checkCompletionVariants("""
           \module M \where \private {
             \func foo (n : Nat) => n
             \func bar => foo 0
             
             \func lol => {-caret-}
           }
          
        """, listOf("foo", "bar"), CompletionCondition.CONTAINS)

    /* module completion in import commands */

    fun `test module name completion`() = doSingleCompletionMultifile(
        """
                -- ! Main.ard
                \import My{-caret-}

                -- ! MyModule.ard
                -- empty
            """,
        """
                \import MyModule
            """
    )

    fun `test directory name completion`() = doSingleCompletionMultifile(
        """
                -- ! Main.ard
                \import Dir{-caret-}

                -- ! Directory/MyModule.ard
                -- empty
            """,
        """
                \import Directory{-caret-}
            """
    )

    fun `test module name completion subdirectory`() = doSingleCompletionMultifile(
        """
                -- ! Main.ard
                \import Directory.My{-caret-}

                -- ! Directory/MyModule.ard
                -- empty
            """,
        """
                \import Directory.MyModule{-caret-}
            """
    )

    /* no variants delegator */

    fun `test noVariantsDelegator`() = doSingleCompletionMultifile("""
                -- ! Main.ard
                \import Depend{-caret-}
                -- ! Dir1/Dependency.ard
                -- empty
    """, """
                \import Dir1.Dependency{-caret-}
    """)

    fun `test noVariantsDelegator 2`() =
        checkSingleCompletion("""
           \module M \where {
             \func foo (n : Nat) => n
             \func bar => foo 0
           }

           \func lol => fo{-caret-}
        """, "M.foo")
}