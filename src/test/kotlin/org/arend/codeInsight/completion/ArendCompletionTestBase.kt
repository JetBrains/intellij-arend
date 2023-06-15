package org.arend.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.hasCaretMarker
import org.arend.replaceCaretMarker

abstract class ArendCompletionTestBase : ArendTestBase() {

    protected fun checkSingleCompletion(code: String, target: String) {
        InlineFile(code).withCaret()
        executeSoloCompletion()

        val normName = target.substringAfterLast(".")
        var element = myFixture.file.findElementAt(myFixture.caretOffset - 2)
        if (element is PsiWhiteSpace) element = element.prevSibling //Needed to correctly process the situation when braces {} are inserted after a keyword

        val skipTextCheck = normName.isEmpty() || normName.contains(' ')
        check((element != null) &&
                (skipTextCheck || element.text == normName) &&
                (element.fitsHierarchically(target) || element.fitsLinearly(target))) {
            "Wrong completion, expected `$target`, but got\n${myFixture.file.text}"
        }
    }

    enum class CompletionCondition {CONTAINS, SAME_ELEMENTS, SAME_KEYWORDS, DOES_NOT_CONTAIN}

    protected fun checkCompletionVariants(code: String, variants: List<String>, condition: CompletionCondition = CompletionCondition.SAME_ELEMENTS, withKeywords: Boolean = true) {
        InlineFile(code).withCaret()

        val result : List<String> = (myFixture.getCompletionVariants("Main.ard") ?: error("Null completion variants")).let { list ->
            if (withKeywords) list else list.filter { !it.startsWith('\\') }
        }

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
            CompletionCondition.SAME_ELEMENTS    -> symDiff(variants, result)
            CompletionCondition.SAME_KEYWORDS    -> symDiff(variants, result.filter { it.startsWith("\\") })
            CompletionCondition.CONTAINS         -> if (!(result.containsAll(variants))) "Completion variants do not contain the expected elements ${variants.minus(result)}" else null
            CompletionCondition.DOES_NOT_CONTAIN -> if (!result.intersect(variants).isEmpty()) "Unexpected completion variants ${result.intersect(variants)}" else null}

        if (errorMessage != null) throw Exception(errorMessage)
    }

    protected fun checkKeywordCompletionVariants(variants: List<String>, condition: CompletionCondition, vararg code: String){
        var failed = false
        var failString = ""
        var successString = ""

        fun completionOk(piece: String) {
            //System.out.println("*** Testing: $piece ***")
            var succeeded = false

            if (variants.size == 1 && condition != CompletionCondition.DOES_NOT_CONTAIN) {
                try {
                    checkSingleCompletion(piece, variants[0])
                    succeeded = true
                } catch (e: Exception) {

                }
            }
            if (!succeeded) {
                try {
                    checkCompletionVariants(piece, variants, condition)
                } catch (e: Exception) {
                    e.printStackTrace()
                    System.err.flush()
                    failString += "$piece\n"
                    failed = true
                    return
                }
            }
            successString += "$piece\n"
        }

        for (codePiece in code) {
            completionOk(codePiece)
            completionOk(codePiece.replace(CARET_MARKER, "\\$CARET_MARKER", false))
        }

        if (failed) {
            System.err.println("\nFailed on:\n$failString")
            System.err.println("\nSucceeded on:\n$successString")
            TestCase.fail()
        }
    }

    protected fun doSingleCompletion(
            before: String,
            after: String
    ) {
        check(hasCaretMarker(before) && hasCaretMarker(after)) {
            "Please add `$CARET_MARKER` marker"
        }
        checkByText(before, after) { executeSoloCompletion() }
    }

    protected fun doSingleCompletionMultifile(
            @Language("Arend") before: String,
            @Language("Arend") after: String
    ) {
        fileTreeFromText(before).createAndOpenFileWithCaretMarker()
        executeSoloCompletion()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun checkContainsCompletion(text: String, @Language("Arend") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        variants.filter { it.lookupString == text }.forEach { return }
        error("Expected completions that contain $text, but got ${variants.toList()}")
    }

    protected fun checkNoCompletion(@Language("Arend") code: String) {
        InlineFile(code).withCaret()
        noCompletionCheck()
    }

    protected fun checkNoCompletionWithMultifile(@Language("Arend") code: String) {
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

    private fun executeSoloCompletion() {
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
