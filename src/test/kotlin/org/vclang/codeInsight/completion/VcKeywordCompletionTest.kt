package org.vclang.codeInsight.completion

class VcKeywordCompletionTest : VcCompletionTestBase() {
    private val fixityKws = listOf("\\fix", "\\fixl", "\\fixr", "\\infix", "\\infixl", "\\infixr")

    fun `test fixity completion after func`() =
            checkCompletionVariants("\\func {-caret-} test => 0", fixityKws)

    fun `test fixity completion after func 2`() =
            checkCompletionVariants("\\func \\{-caret-} test => 0", fixityKws)

    fun `test fixity completion after class`() =
            checkCompletionVariants("\\class {-caret-} testClass {}", fixityKws)

    fun `test fixity completion after class 2`() =
            checkCompletionVariants("\\class \\{-caret-} testClass {}", fixityKws)

    fun `test fixity completion after data`() =
            checkCompletionVariants("\\data {-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after data 2`() =
            checkCompletionVariants("\\data \\{-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after as`() =
            checkCompletionVariants(
                    """
                        \import B (lol \as {-caret-} +)

                        -- B.vc
                        \func lol (a b : Nat) => a
                    """, fixityKws)

    /* fun `test fixity completion after as 2`() =
            checkCompletionVariants(
                    """
                        \import B (lol \as \\{-caret-} +)

                        -- B.vc
                        \func lol (a b : Nat) => a
                    """, fixityKws) */ //TODO: Fix me

    fun `test fixity completion after simple datatype constructor`() =
            checkCompletionVariants("\\data MyNat | {-caret-} myzero", fixityKws)

    fun `test fixity completion after simple datatype constructor 2`() =
            checkCompletionVariants("\\data MyNat | \\{-caret-} myzero", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 2`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => \\{-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after class field`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-} * : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field 2`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | \\{-caret-} * : El -> El -> El}", fixityKws)

    /* fun `test fixity completion after class field synonym`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | * : El -> El -> El}\n" +
                                    "\\class AddMonoid => Monoid { | * => {-caret-} +}", fixityKws) */ //TODO: Fix me

    fun `test fixity completion after class field synonym 2`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | * : El -> El -> El}\n" +
                                    "\\class AddMonoid => Monoid { | * => \\{-caret-} +}", fixityKws)


}