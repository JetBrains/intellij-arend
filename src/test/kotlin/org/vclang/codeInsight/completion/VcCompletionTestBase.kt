package org.vclang.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase
import org.vclang.fileTreeFromText
import org.vclang.hasCaretMarker
import org.vclang.replaceCaretMarker

abstract class VcCompletionTestBase : VcTestBase() {

    protected fun checkSingleCompletion(@Language("Vclang") code: String, target: String) {
        InlineFile(code).withCaret()
        executeSoloCompletion()

        val normName = target.substringAfterLast(".")
        val element = myFixture.file.findElementAt(myFixture.caretOffset - 2)!!
        val skipTextCheck = normName.isEmpty() || normName.contains(' ')
        check((skipTextCheck || element.text == normName)
                && (element.fitsHierarchically(target) || element.fitsLinearly(target))) {
            "Wrong completion, expected `$target`, but got\n${myFixture.file.text}"
        }
    }

    enum class CompletionCondition {CONTAINS, SAME_ELEMENTS, SAME_KEYWORDS, DOES_NOT_CONTAIN}

    protected fun checkCompletionVariants(@Language("Vclang") code: String, variants: List<String>, condition: CompletionCondition = CompletionCondition.SAME_ELEMENTS) {
        InlineFile(code).withCaret()

        val result = myFixture.getCompletionVariants("Main.vc")
        assertNotNull(result)

        fun symDiff(required: List<String>, actual: List<String>): String? {
            if (HashSet(required) == HashSet(actual)) return null
            var resultMessage = ""
            val rma = required.minus(actual)
            val amr = actual.minus(required)
            if (rma.isNotEmpty()) resultMessage += "Completion variants do not contain the expected elements $rma"
            if (amr.isNotEmpty()) resultMessage += (if (resultMessage.isEmpty())  "" else "; ") + "Unexpected completion variants: $amr"
            return resultMessage
        }

        val errorMessage: String? = when (condition) {
            CompletionCondition.SAME_ELEMENTS    -> symDiff(variants, result!!)
            CompletionCondition.SAME_KEYWORDS    -> symDiff(variants, result!!.filter { it.startsWith("\\") })
            CompletionCondition.CONTAINS         -> if (!(result!!.containsAll(variants))) "Completion variants do not contain the expected elements ${variants.minus(result)}" else null
            CompletionCondition.DOES_NOT_CONTAIN -> if (!result!!.intersect(variants).isEmpty()) "Unexpected completion variants ${result.intersect(variants)}" else null}

        if (errorMessage != null) throw Exception(errorMessage)
    }

    protected fun checkKeywordCompletionVariants(variants: List<String>, condition: CompletionCondition, @Language("Vclang") vararg code: String){
        var failed = false
        var failString = ""
        var successString = ""
        var index = 0
        for (codePiece in code) {
            System.out.println("*** Testing: $codePiece ***")
            val codePieceWithBackSlash = codePiece.replace("{-caret-}", "\\{-caret-}", false)
            var failedTest = false
            try {
                checkCompletionVariants(codePiece, variants, condition)
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.flush()
                failedTest = true
                failString += "$codePiece\n"
            }
            System.out.println("*** Testing: $codePieceWithBackSlash ***")

            if (!failedTest) successString += "$codePiece\n"
            failed = failed || failedTest
            failedTest = false

            try {
                if (variants.size == 1 &&
                        (condition == CompletionCondition.SAME_ELEMENTS || condition == CompletionCondition.SAME_KEYWORDS))
                    checkSingleCompletion(codePieceWithBackSlash, variants[0]) else
                    checkCompletionVariants(codePieceWithBackSlash, variants, condition)
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.flush()
                failedTest = true
                failString += "$codePieceWithBackSlash\n"
            }
            index++
            failed = failed || failedTest
            if (!failedTest) successString += "$codePieceWithBackSlash\n"
        }

        if (failed) {
            System.err.println("\nFailed on:\n$failString")
            System.err.println("\nSucceeded on:\n$successString")
            TestCase.fail()
        }
    }

    protected fun doSingleCompletion(
            @Language("Vclang") before: String,
            @Language("Vclang") after: String
    ) {
        check(hasCaretMarker(before) && hasCaretMarker(after)) {
            "Please add `{-caret-}` marker"
        }
        checkByText(before, after) { executeSoloCompletion() }
    }

    protected fun doSingleCompletionMultiflie(
            @Language("Vclang") before: String,
            @Language("Vclang") after: String
    ) {
        fileTreeFromText(before).createAndOpenFileWithCaretMarker()
        executeSoloCompletion()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun checkContainsCompletion(text: String, @Language("Vclang") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        variants.filter { it.lookupString == text }.forEach { return }
        error("Expected completions that contain $text, but got ${variants.toList()}")
    }

    protected fun checkNoCompletion(@Language("Vclang") code: String) {
        InlineFile(code).withCaret()
        noCompletionCheck()
    }

    protected fun checkNoCompletionWithMultifile(@Language("Vclang") code: String) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        noCompletionCheck()
    }

    private fun noCompletionCheck() {
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(variants.isEmpty()) {
            "Expected zero completions, got ${variants.size}."
        }
    }

    protected fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()
        if (variants != null) {
            fun LookupElement.debug(): String = "$lookupString ($psiElement)"
            error("Expected a single completion, but got ${variants.size}\n"
                    + variants.joinToString("\n") { it.debug() })
        }
    }

    private fun PsiElement.fitsHierarchically(target: String): Boolean = when {
        text == target -> true
        text.length > target.length -> false
        else -> parent?.fitsHierarchically(target) ?: false
    }

    private fun PsiElement.fitsLinearly(target: String): Boolean =
            checkLinearly(target, Direction.LEFT) || checkLinearly(target, Direction.RIGHT)

    private fun PsiElement.checkLinearly(target: String, direction: Direction): Boolean {
        var el = this
        var text = ""
        while (text.length < target.length) {
            text = if (direction == Direction.LEFT) el.text + text else text + el.text
            if (text == target) return true
            el = (if (direction == Direction.LEFT) {
                PsiTreeUtil.prevVisibleLeaf(el)
            } else {
                PsiTreeUtil.nextVisibleLeaf(el)
            }) ?: break
        }
        return false
    }

    private enum class Direction {
        LEFT,
        RIGHT
    }
}
