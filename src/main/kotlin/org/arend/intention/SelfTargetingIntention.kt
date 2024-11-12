package org.arend.intention

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ancestors
import org.arend.util.checkArcFile

// code borrowed from kotlin plugin

@Suppress("EqualsOrHashCode")
abstract class SelfTargetingIntention<T : PsiElement>(
        val elementType: Class<T>,
        private var text: String,
        private val familyName: String = text
) : IntentionAction {

    protected val defaultText: String = text

    protected fun setText(text: String) {
        this.text = text
    }

    final override fun getText() = text
    final override fun getFamilyName() = familyName

    abstract fun isApplicableTo(element: T, caretOffset: Int, editor: Editor): Boolean

    abstract fun applyTo(element: T, project: Project, editor: Editor)

    private fun getTarget(editor: Editor, file: PsiFile): T? {
        if (checkArcFile(file.virtualFile)) {
            return null
        }
        if (editor.isViewer || !BaseArendIntention.canModify(file)) {
            return null
        }

        val offset = editor.caretModel.offset
        val leaf1 = file.findElementAt(offset)
        val leaf2 = file.findElementAt(offset - 1)
        val commonParent = if (leaf1 != null && leaf2 != null) PsiTreeUtil.findCommonParent(leaf1, leaf2) else null

        val elementsToCheck = sequence {
            if (leaf1 != null) {
                yieldAll(leaf1.ancestors.takeWhile { it != commonParent })
            }
            if (leaf2 != null) {
                yieldAll(leaf2.ancestors.takeWhile { it != commonParent })
            }
            if (commonParent != null && commonParent !is PsiFile) {
                yieldAll(commonParent.ancestors)
            }
        }

        for (element in elementsToCheck) {
            if (elementType.isInstance(element)) {
                val tElement = elementType.cast(element)
                if (isApplicableTo(tElement, offset, editor)) {
                    return tElement
                }
            }
            if (forbidCaretInsideElement(element)) {
                val textRange = element.textRange
                if (textRange.startOffset < offset && offset < textRange.endOffset) break
            }
        }
        return null
    }

    protected open fun forbidCaretInsideElement(element: PsiElement): Boolean = false


    final override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        getTarget(editor, file) != null

    final override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        editor ?: return
        val target = getTarget(editor, file) ?: return
        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return
        applyTo(target, project, editor)
    }

    override fun startInWriteAction() = true

    override fun toString(): String = getText()

    override fun equals(other: Any?): Boolean {
        if (other is IntentionWrapper) return this == other.action
        return other is SelfTargetingIntention<*> && javaClass == other.javaClass && text == other.text
    }

}

abstract class SelfTargetingRangeIntention<T : PsiElement>(
        elementType: Class<T>,
        text: String,
        familyName: String = text
) : SelfTargetingIntention<T>(elementType, text, familyName) {

    abstract fun applicabilityRange(element: T): TextRange?

    final override fun isApplicableTo(element: T, caretOffset: Int, editor: Editor): Boolean {
        val range = applicabilityRange(element) ?: return false
        return range.containsOffset(caretOffset)
    }
}