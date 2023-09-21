package org.arend.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiCodeFragmentImpl
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.arend.ArendLanguage
import org.arend.IArendFile
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.parser.ArendParser
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendExpr
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.resolving.ArendReference
import java.util.concurrent.atomic.AtomicLong

class ArendExpressionCodeFragment(project: Project, expression: String,
                                  context: PsiElement?,
                                  private val fragmentController: ArendCodeFragmentController?):
    PsiCodeFragmentImpl(project, ArendExpressionCodeFragmentElementType, true, "fragment.ard", expression, context), IArendFile {
    override var lastModification = AtomicLong(-1)

    override fun getReference(): ArendReference? = null

    override val scope: Scope get() = MergeScope(fragmentController?.getFragmentScope(this) ?: EmptyScope.INSTANCE, (context as? ArendCompositeElement)?.scope ?: EmptyScope.INSTANCE)

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    fun getExpr(): ArendExpr? {
        val firstChild = firstChild
        return if (firstChild is ArendExpr && firstChild.elementType != ArendElementTypes.EXPR) firstChild else null
    }

    fun fragmentResolved() { fragmentController?.expressionFragmentResolved(this) }

    fun scopeModified(nsCmd: NsCmdRefactoringAction) { fragmentController?.scopeModified(nsCmd) }
}

abstract class ArendCodeFragmentElementType(debugName: String, val elementType: IElementType) : ICodeFragmentElementType(debugName, ArendLanguage.INSTANCE) {
    override fun parseContents(chameleon: ASTNode): ASTNode {
        val project: Project = chameleon.psi.project
        var builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, ArendLanguage.INSTANCE, chameleon.chars)
        val parser = ArendParser()
        builder = GeneratedParserUtilBase.adapt_builder_(this, builder, parser, ArendParser.EXTENDS_SETS_)
        val marker = GeneratedParserUtilBase.enter_section_(builder, 0, GeneratedParserUtilBase._COLLAPSE_ , null)
        val success = doParse(builder) //ArendParser.longName(builder, 1)
        GeneratedParserUtilBase.exit_section_(builder, 0, marker, elementType, success, true, GeneratedParserUtilBase.TRUE_CONDITION)
        return builder.treeBuilt
    }

    abstract fun doParse(builder: PsiBuilder): Boolean
}

object ArendExpressionCodeFragmentElementType : ArendCodeFragmentElementType("AREND_EXPRESSION_CODE_FRAGMENT", ArendElementTypes.EXPR) {
    override fun doParse(builder: PsiBuilder): Boolean = ArendParser.expr(builder, 1, -1)
}

interface ArendCodeFragmentController {
    fun expressionFragmentResolved(codeFragment: ArendExpressionCodeFragment)

    fun scopeModified(deferredNsCmd: NsCmdRefactoringAction)

    fun getFragmentScope(codeFragment: ArendExpressionCodeFragment): Scope
}