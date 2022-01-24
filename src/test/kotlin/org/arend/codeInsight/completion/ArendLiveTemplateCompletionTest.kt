package org.arend.codeInsight.completion

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor

class ArendLiveTemplateCompletionTest : ArendCompletionTestBase() {
    override fun setUp() {
        super.setUp()
        LiveTemplateCompletionContributor.setShowTemplatesInTests(true, testRootDisposable)
    }

    fun `test func template`() = checkCompletionVariants(
        "\\fun{-caret-}", listOf("\\func=>"), CompletionCondition.CONTAINS
    )

    fun `test case-with template`() = checkCompletionVariants(
        "\\func test => \\cas{-caret-}", listOf("\\case\\with"), CompletionCondition.CONTAINS
    )

    fun `test no func template in comments`() = checkCompletionVariants(
        "-- \\fun{-caret-}", listOf("\\func=>"), CompletionCondition.DOES_NOT_CONTAIN
    )

    fun `test no case-with template in comments`() = checkCompletionVariants(
        "\\func test => -- \\cas{-caret-}", listOf("\\case\\with"), CompletionCondition.DOES_NOT_CONTAIN
    )

    fun `test no func template in expression context`() = checkCompletionVariants(
        "\\func test => \\fun{-caret-}", listOf("\\func=>"), CompletionCondition.DOES_NOT_CONTAIN
    )

    fun `test no case-with template in file context`() = checkCompletionVariants(
        "\\cas{-caret-}", listOf("\\case\\with"), CompletionCondition.DOES_NOT_CONTAIN
    )
}