package org.arend.navigation

import com.intellij.ide.navigationToolbar.DefaultNavBarExtension
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.psi.parentOfType
import org.arend.structure.ArendStructureViewElement
import org.arend.term.group.Group
import javax.swing.Icon

class ArendNavBarExtension : StructureAwareNavBarModelExtension() {
    override val language: Language
        get() = ArendLanguage.INSTANCE

    private fun checkCoClause(coClause: ArendLocalCoClause?): Pair<Boolean, ArendLocalCoClause?> {
        return if (coClause == null) {
            Pair(false, null)
        } else if (coClause.parent is ArendFunctionBody || coClause.parent.parent is ArendFunctionBody || coClause.parent.parent is ArendReturnExpr || coClause.parent is ArendClassStat) {
            Pair(true, coClause)
        } else {
            checkCoClause(coClause.parentOfType())
        }
    }

    override fun getPresentableText(element: Any?): String? {
        val psiElement = element as? PsiElement?
        val coClauseResult = checkCoClause(psiElement?.ancestor<ArendLocalCoClause>())
        val overrideField = psiElement?.ancestor<ArendOverriddenField>()
        val group = psiElement?.ancestor<PsiDefReferable>()
        return if (psiElement is ArendFile) {
            psiElement.refName
        } else if (coClauseResult.first) {
            coClauseResult.second?.longName?.referenceName
        } else if (overrideField != null) {
            overrideField.overriddenField?.referenceName
        } else {
            (psiElement as? PsiNamedElement)?.name
                ?: group?.name
                ?: DefaultNavBarExtension().getPresentableText(element)
        }
    }

    override fun getParent(psiElement: PsiElement?): PsiElement? {
        val coClauseResult = checkCoClause(psiElement?.parentOfType<ArendLocalCoClause>())
        if (coClauseResult.first) {
            return coClauseResult.second
        }
        val overrideField = psiElement?.parentOfType<ArendOverriddenField>()
        if (overrideField != null) {
            return overrideField
        }
        return psiElement?.parentOfType<PsiDefReferable>() ?: DefaultNavBarExtension().getParent(psiElement)
    }

    override fun getIcon(element: Any?): Icon? {
        val psiElement = element as? PsiElement?
        val coClause = psiElement?.ancestor<ArendLocalCoClause>()
        val overrideField = psiElement?.ancestor<ArendOverriddenField>()
        return if (checkCoClause(coClause).first) {
            ArendIcons.COCLAUSE_DEFINITION
        } else if (overrideField != null) {
            ArendIcons.CLASS_FIELD
        } else if (element is Iconable) {
            element.getIcon(0)
        } else {
            DefaultNavBarExtension().getIcon(element)
        }
    }

    override fun getLeafElement(dataContext: DataContext): PsiElement? {
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val element = psiFile.findElementAt(editor.caretModel.offset)
        val coClauseResult = checkCoClause(element?.ancestor<ArendLocalCoClause>())
        return if (element?.ancestor<ArendStatCmd>()?.importKw != null) {
            null
        } else if (coClauseResult.first) {
            coClauseResult.second
        } else {
            (element?.ancestor<ArendOverriddenField>() ?: (element?.ancestor<PsiDefReferable>() ?: element))
        }
    }

    override fun childrenFromNodeAndProviders(parent: StructureViewTreeElement): List<TreeElement> {
        val group = ((parent as? ArendStructureViewElement?)?.psi as? Group?)
        val allChildren = group?.let { ((it as? ArendDefClass?)?.classStatList?.mapNotNull { stat -> stat.classImplement ?: stat.overriddenField } ?: emptyList()) +
                it.internalReferables + it.statements.mapNotNull { statement -> statement.group } + it.statements.filter { statement -> statement.group == null } + it.dynamicSubgroups  }
                ?: emptyList()
        return if (group != null && group !is ArendFile) {
            allChildren.mapNotNull { e -> (e as? ArendCompositeElement)?.let { ArendStructureViewElement(it) } }
        } else {
            super.childrenFromNodeAndProviders(parent)
        }
    }
}
