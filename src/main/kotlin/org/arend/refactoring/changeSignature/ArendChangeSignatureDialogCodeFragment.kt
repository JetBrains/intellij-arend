package org.arend.refactoring.changeSignature

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiCodeFragmentImpl
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import org.arend.ArendLanguage
import org.arend.IArendFile
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.parser.ArendParser
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.ArendCompositeElement
import org.arend.resolving.ArendReference
import java.util.concurrent.atomic.AtomicLong

class ArendChangeSignatureDialogCodeFragment(project: Project, expression: String, context: PsiElement?, val parametersModel: ArendParameterTableModel, val parameter: ArendParameterInfo? = null /* parameter == null means this fragment pertains to the return type */):
    PsiCodeFragmentImpl(project, ArendExpressionCodeFragmentElementType, true, "fragment.ard", expression, context), IArendFile {
    override var lastModification = AtomicLong(-1)
    override fun getReference(): ArendReference? = null

    override val scope: Scope
        get() {
        val items = parametersModel.items
        val limit = items.indexOfFirst { it.parameter == parameter }.let { if (it == -1) items.size else it }

        val params = items.take(limit).map { it.associatedReferable }
        val baseScope = (context as? ArendCompositeElement)?.scope ?: EmptyScope.INSTANCE
        return MergeScope(ListScope(params), baseScope)
    }

    override fun moduleTextRepresentation(): String  = name
    override fun positionTextRepresentation(): String? = null

    fun resetDependencies() {
        val item = parametersModel.items.firstOrNull { it.parameter == parameter }
        item?.dependencies?.clear()
    }
    fun addDependency(dependency: ArendChangeSignatureDialogParameter) {
        val item = parametersModel.items.firstOrNull { it.parameter == parameter }
        val dep = parametersModel.items.firstOrNull {it.associatedReferable == dependency }
        if (item != null && dep != null) item.dependencies.add(dep)
    }
}

object ArendExpressionCodeFragmentElementType: ICodeFragmentElementType("EXPR_TEXT", ArendLanguage.INSTANCE) {
    override fun parseContents(chameleon: ASTNode): ASTNode {
        val project: Project = chameleon.psi.project
        var builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, ArendLanguage.INSTANCE, chameleon.chars)
        val parser = ArendParser()
        builder = GeneratedParserUtilBase.adapt_builder_(this, builder, parser, ArendParser.EXTENDS_SETS_)
        val marker = GeneratedParserUtilBase.enter_section_(builder, 0, GeneratedParserUtilBase._COLLAPSE_ , null)
        val success = ArendParser.expr(builder, 1, -1)
        GeneratedParserUtilBase.exit_section_(builder, 0, marker, ArendElementTypes.EXPR, success, true, GeneratedParserUtilBase.TRUE_CONDITION)
        return builder.treeBuilt
    }
}