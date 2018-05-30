package org.vclang.codeInsight.completion

import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FIXITY_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.GLOBAL_STATEMENT_KWS
import org.vclang.psi.VcElementTypes
import org.vclang.psi.VcElementTypes.*
import java.util.Collections.singletonList

class VcKeywordCompletionTest : VcCompletionTestBase() {
    private val fixityKws = FIXITY_KWS.map { it.toString() }
    private val statementKws = STATEMENT_KWS.map { it.toString() }
    private val globalStatementKws = GLOBAL_STATEMENT_KWS.map { it.toString() }
    private val importKw = singletonList(IMPORT_KW.toString())
    private val asKw = singletonList(AS_KW.toString())
    private val hidingKw = singletonList(HIDING_KW.toString())
    private val whereKw = singletonList(WHERE_KW.toString())
    private val extendsKw = singletonList(EXTENDS_KW.toString())
    private val huKw = listOf(USING_KW.toString(), HIDING_KW.toString())
    private val dataKw = VcElementTypes.DATA_KW.toString()

    fun `test fixity completion after func 1`() =
            checkKeywordCompletionVariants("\\func {-caret-}test => 0", fixityKws)

    fun `test fixity completion after func 2`() =
            checkKeywordCompletionVariants("\\func {-caret-}", fixityKws)

    fun `test fixity completion after class 1`() =
            checkKeywordCompletionVariants("\\class {-caret-}testClass {}", fixityKws)

    fun `test fixity completion after class 2`() =
            checkKeywordCompletionVariants("\\class {-caret-}", fixityKws)

    fun `test fixity completion after data 1`() =
            checkKeywordCompletionVariants("\\data {-caret-}MyNat | myzero", fixityKws)

    fun `test fixity completion after data 2`() =
            checkKeywordCompletionVariants("\\data {-caret-}", fixityKws)

    fun `test fixity completion after as 1`() =
            checkKeywordCompletionVariants("\\import B (lol \\as {-caret-}+)", fixityKws)

    fun `test fixity completion after simple datatype constructor 1`() =
            checkKeywordCompletionVariants("\\data MyNat | {-caret-}myzero", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 1`() =
            checkKeywordCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after class field 1`() =
            checkKeywordCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field synonym 1`() =
            checkKeywordCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-}+}", fixityKws)

    fun `test no fixity completion in pattern matching`() =
            checkKeywordCompletionVariants("\\fun foo (n : Nat) \\elim n | {-caret-}zero =>", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no fixity completion after func fat arrow`() =
            checkKeywordCompletionVariants("\\fun foo (n : Nat) => {-caret-}n ", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test as completion in namespace command`() =
            checkCompletionVariants("\\import B (lol {-caret-})", asKw)

    fun `test as completion in namespace command 2`() =
            checkSingleCompletion("\\import B (lol \\{-caret-})", asKw[0])

    fun `test nsCmd completion in namespace command 1`() =
            checkCompletionVariants("\\import B (lol) {-caret-}", hidingKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 2`() =
            checkSingleCompletion("\\import B (lol) \\{-caret-}", hidingKw[0])

    fun `test nsCmd completion in namespace command 3`() =
            checkKeywordCompletionVariants("\\import B (lol)\n{-caret-}", hidingKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 4`() =
            checkKeywordCompletionVariants("\\import B {-caret-}", huKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 5`() =
            checkKeywordCompletionVariants("\\import B {-caret-}(lol)", huKw)

    fun `test nsCmd completion in namespace command 8`() =
            checkNoCompletion("\\import B {-caret-}\\using (lol)")

    fun `test nsCmd completion in namespace command 9`() =
            checkNoCompletion("\\import B \\using (lol) {-caret-} \\hiding (lol)")

    fun `test nsCmd completion in namespace command 10`() =
            checkNoCompletion("\\import B \\hiding {-caret-} (lol)")

    fun `test nsCmd completion in namespace command 11`() =
            checkNoCompletion("\\import B \\hiding {-caret-}")

    fun `test nsCmd completion in namespace command 12`() =
            checkCompletionVariants("\\import {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root keywords completion 1`() =
            checkKeywordCompletionVariants("\\import B\n {-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 2`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0\n {-caret-}\\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 3`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar\n {-caret-}\\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 4`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 5`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 6`() =
            checkKeywordCompletionVariants("\\import B \\hiding (a)\n{-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 7`() =
            checkKeywordCompletionVariants("\\func f (xs : Nat) : Nat \\elim xs\n | suc x => \\case x \\with {| zero => 0 | suc _ => 1}\n {-caret-}", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 8`() =
            checkKeywordCompletionVariants("\\class A {| foo : Nat}\n\\func f => \\new A {| foo => 0 |\n{-caret-}=> 1\n}\n", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 1`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 2`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root completion in empty context`() =
            checkKeywordCompletionVariants("{-caret-}", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test completion after truncated`() =
            checkCompletionVariants("\\truncated {-caret-}", statementKws.minus("\\data"), CompletionCondition.DOES_NOT_CONTAIN)

    fun `test completion after truncated 2`() =
            checkSingleCompletion("\\tru{-caret-}", "\\data")

    fun `test completion after truncated 3`() =
            checkCompletionVariants("\\truncated {-caret-}", singletonList(dataKw), CompletionCondition.CONTAINS)

    fun `test completion after truncated 4`() =
            checkSingleCompletion("\\truncated \\{-caret-}", dataKw)

    fun `test completion after truncated 5`() =
            checkSingleCompletion("\\truncated \\da{-caret-}", dataKw)

    fun `test completion after truncated 6`() =
            checkSingleCompletion("\\tru{-caret-}\\func", "\\truncated \\data \\func")

    fun `test no keyword completion after instance` () =
            checkKeywordCompletionVariants("\\instance {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no keyword completion after open` () =
            checkKeywordCompletionVariants("\\open {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 1`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) : Nat => {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 2`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) : {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 3` () =
            checkKeywordCompletionVariants("\\func f ({-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion in empty context`() =
            checkKeywordCompletionVariants("{-caret-}", whereKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion after import`() =
            checkKeywordCompletionVariants("\\import A\n{-caret-}", whereKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion after open`() =
            checkKeywordCompletionVariants("\\open Nat\n{-caret-}", whereKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test where completion after func`() =
            checkKeywordCompletionVariants("\\func lol => 0\n{-caret-}", whereKw, CompletionCondition.CONTAINS)

    fun `test where completion after data`() =
            checkKeywordCompletionVariants("\\data Lol | lol1 | lol2\n{-caret-}", whereKw, CompletionCondition.CONTAINS)

    fun `test where completion after class`() =
            checkKeywordCompletionVariants("\\class Lol {}\n{-caret-}", whereKw, CompletionCondition.CONTAINS)

    fun `test where completion after iterated where`() =
            checkKeywordCompletionVariants("\\func foo => 0 \\where \\func bar => 0\n{-caret-}", whereKw, CompletionCondition.CONTAINS)

    fun `test no where completion after iterated where`() =
            checkKeywordCompletionVariants("\\func foo => 0 \\where {\\func bar => 0}\n{-caret-}", whereKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no keyword completion before crlf`() =
            checkKeywordCompletionVariants("\\func foo => 0 {-caret-}\n", globalStatementKws + whereKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test extends completion after class name`() =
            checkCompletionVariants("\\class Lol {-caret-}", extendsKw, CompletionCondition.CONTAINS)

    fun `test extends completion after class name 2`() =
            checkSingleCompletion("\\class Lol \\{-caret-}", extendsKw[0])

    fun `test extends completion after class name 3`() =
            checkCompletionVariants("\\class Lol {-caret-}{}", extendsKw, CompletionCondition.CONTAINS)

    fun `test extends completion after class name 4`() =
            checkSingleCompletion("\\class Lol \\{-caret-}{}", extendsKw[0])

    fun `test extends completion after class arguments`() =
            checkCompletionVariants("\\class Lol (n: Nat) {-caret-}", extendsKw, CompletionCondition.CONTAINS) //TODO: Should work without additional space

    fun `test extends completion after class arguments 2`() =
            checkSingleCompletion("\\class Lol (n: Nat) \\{-caret-}", extendsKw[0])

    fun `test extends completion after class arguments 3`() =
            checkKeywordCompletionVariants("\\class Lol (n: Nat) {-caret-}{}", extendsKw, CompletionCondition.CONTAINS)

    fun `test no extends after class without name`() =
            checkKeywordCompletionVariants("\\class {-caret-}{}", extendsKw, CompletionCondition.DOES_NOT_CONTAIN)

    //fun `test no extends inside class arguments block`() = checkKeywordCompletionVariants("\\class Lol (n: Nat) {-caret-} (m: Nat){}", extendsKw, CompletionCondition.DOES_NOT_CONTAIN) //TODO: Test when multiple fields are supported


}