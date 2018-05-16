package org.vclang.codeInsight.completion

import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FIXITY_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.ROOT_KWS
import java.util.Collections.singletonList

class VcKeywordCompletionTest : VcCompletionTestBase() {
    private val fixityKws = FIXITY_KWS.map { it.toString() }
    private val rootKws = ROOT_KWS.map { it.toString() }

    fun `test fixity completion after func`() =
            checkCompletionVariants("\\func {-caret-} test => 0", fixityKws)

    fun `test fixity completion after func 2`() =
            checkCompletionVariants("\\func \\{-caret-} test => 0", fixityKws)

    fun `test fixity completion after func 3`() =
            checkCompletionVariants("\\func {-caret-}test => 0", fixityKws)

    fun `test fixity completion after class`() =
            checkCompletionVariants("\\class {-caret-} testClass {}", fixityKws)

    fun `test fixity completion after class 2`() =
            checkCompletionVariants("\\class \\{-caret-} testClass {}", fixityKws)

    fun `test fixity completion after class 3`() =
            checkCompletionVariants("\\class {-caret-}testClass {}", fixityKws)

    fun `test fixity completion after data`() =
            checkCompletionVariants("\\data {-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after data 2`() =
            checkCompletionVariants("\\data \\{-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after data 3`() =
            checkCompletionVariants("\\data {-caret-}MyNat | myzero", fixityKws)

    fun `test fixity completion after as`() =
            checkCompletionVariants("\\import B (lol \\as {-caret-} +)", fixityKws)

    fun `test fixity completion after as 2`() =
            checkCompletionVariants("\\import B (lol \\as \\{-caret-} +)", fixityKws)

    fun `test fixity completion after as 3`() =
            checkCompletionVariants("\\import B (lol \\as {-caret-}+)", fixityKws)

    fun `test fixity completion after simple datatype constructor`() =
            checkCompletionVariants("\\data MyNat | {-caret-} myzero", fixityKws)

    fun `test fixity completion after simple datatype constructor 2`() =
            checkCompletionVariants("\\data MyNat | \\{-caret-} myzero", fixityKws)

    fun `test fixity completion after simple datatype constructor 3`() =
            checkCompletionVariants("\\data MyNat | {-caret-}myzero", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 2`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => \\{-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 3`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after class field`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-} * : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field 2`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | \\{-caret-} * : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field 3`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field synonym`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-} +}", fixityKws)

    fun `test fixity completion after class field synonym 2`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => \\{-caret-} +}", fixityKws)

    fun `test fixity completion after class field synonym 3`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-}+}", fixityKws)

    fun `test no fixity completion in pattern matching`() =
            checkCompletionVariants("\\fun foo (n : Nat) \\elim n | {-caret-} zero =>", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no fixity completion after func fat arrow`() =
            checkCompletionVariants("\\fun foo (n : Nat) => {-caret-} n ", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test as completion in namespace command`() =
            checkCompletionVariants("\\import B (lol {-caret-})", singletonList("\\as"))

    fun `test as completion in namespace command 2`() =
            checkSingleCompletion("\\as", "\\import B (lol \\{-caret-})")

    fun `test nsCmd completion in namespace command`() =
            checkCompletionVariants("\\import B (lol) {-caret-}", singletonList("\\hiding"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 2`() =
            checkSingleCompletion("\\hiding", "\\import B (lol) \\{-caret-}")

    fun `test nsCmd completion in namespace command 3`() =
            checkCompletionVariants("\\import B {-caret-}", listOf("\\hiding", "\\using"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 4`() =
            checkCompletionVariants("\\import B \\{-caret-}", listOf("\\hiding", "\\using"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 5`() =
            checkCompletionVariants("\\import B {-caret-} (lol)", listOf("\\hiding", "\\using"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 6`() =
            checkCompletionVariants("\\import B \\{-caret-} (lol)", listOf("\\hiding", "\\using"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 7`() =
            checkCompletionVariants("\\import B {-caret-}(lol)", listOf("\\hiding", "\\using"), CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 8`() =
            checkNoCompletion("\\import B {-caret-}\\using (lol)")

    fun `test nsCmd completion in namespace command 9`() =
            checkNoCompletion("\\import B \\using (lol) {-caret-} \\hiding (lol)")


    //fun `test root completion in empty context`() = checkCompletionVariants("{-caret-}", rootKws, CompletionCondition.CONTAINS)


}