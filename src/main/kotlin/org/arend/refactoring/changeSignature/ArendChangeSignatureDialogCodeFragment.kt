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
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.parser.ArendParser
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.ArendCompositeElement
import org.arend.resolving.ArendReference
import java.util.concurrent.atomic.AtomicLong

class ArendChangeSignatureDialogCodeFragment(project: Project, expression: String, val complementScope: () -> Scope?, context: PsiElement?):
    PsiCodeFragmentImpl(project, ArendExpressionCodeFragmentElementType, true, "fragment.ard", expression, context), IArendFile {
    override var lastModification = AtomicLong(-1)
    override fun getReference(): ArendReference? = null

    override val scope: Scope get() {
        val baseScope = (context as? ArendCompositeElement)?.scope ?: EmptyScope.INSTANCE
        return MergeScope(complementScope.invoke() ?: EmptyScope.INSTANCE, baseScope)
    }

    override fun moduleTextRepresentation(): String  = name
    override fun positionTextRepresentation(): String? = null
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