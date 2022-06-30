package org.arend.highlight

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.LevelVariable
import org.arend.core.context.binding.ParamLevelVariable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor

class ArendInlayHighlightingPass(file: ArendFile, editor: Editor) : EditorBoundHighlightingPass(editor, file, true) {
    private val inlays = ArrayList<Pair<PsiElement, String>>()

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (myEditor !is EditorImpl) return
        (myFile as ArendFile).traverseGroup {
            if (it is ArendDefinition) {
                val defId = it.defIdentifier
                if (defId != null) {
                    val builder = StringBuilder()
                    val def = (it.tcReferable as? TCDefReferable)?.typechecked
                    if (def != null && def.levelParameters != null && def.levelParameters.isNotEmpty()) {
                        val levelParams = def.levelParameters
                        if (levelParams[0].type == LevelVariable.LvlType.PLVL && levelParams[0] is ParamLevelVariable && PsiTreeUtil.getChildOfType(it, ArendPLevelParams::class.java) == null) {
                            builder.append(" ")
                            val ppv = PrettyPrintVisitor(builder, 0)
                            ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(0, def.numberOfPLevelParameters)), true)
                        }
                        val lastVar = levelParams[levelParams.size - 1]
                        if (lastVar.type == LevelVariable.LvlType.HLVL && lastVar is ParamLevelVariable && PsiTreeUtil.getChildOfType(it, ArendHLevelParams::class.java) == null) {
                            builder.append(" ")
                            val ppv = PrettyPrintVisitor(builder, 0)
                            ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(def.numberOfPLevelParameters, levelParams.size)), false)
                        }
                    }
                    inlays.add(Pair(defId, builder.toString()))
                }
            }
        }
    }

    override fun doApplyInformationToEditor() {
        val model = myEditor.inlayModel
        for (pair in inlays) {
            val offset = pair.first.endOffset
            for (inlay in model.getInlineElementsInRange(offset, pair.first.extendRight.nextSibling?.startOffset ?: offset)) {
                inlay.dispose()
            }
            if (pair.second.isNotEmpty()) {
                model.addInlineElement(offset, true, PresentationRenderer(PresentationFactory(myEditor as EditorImpl).text(pair.second)))
            }
        }
    }
}