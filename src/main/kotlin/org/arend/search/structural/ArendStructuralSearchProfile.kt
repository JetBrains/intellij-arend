package org.arend.search.structural

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.dupLocator.util.NodeFilter
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.structuralsearch.MalformedPatternException
import com.intellij.structuralsearch.PatternContext
import com.intellij.structuralsearch.PatternContextInfo
import com.intellij.structuralsearch.StructuralSearchProfile
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import org.arend.ArendFileType
import org.arend.ArendLanguage
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ArendRefIdentifier
import org.arend.psi.ArendVisitor
import org.arend.util.ArendBundle

private const val TYPED_VAR_PREFIX = "\$\$\$VAR\$\$\$_"
private val PROOF_SEARCH_CONTEXT = PatternContext("arend-proof-search", ArendBundle.getLazyMessage("arend.structural.search.proof.search"))
private val PATTERN_CONTEXTS = listOf(PROOF_SEARCH_CONTEXT)
private val PATTERN_ERROR = Key<String>("arend.pattern.error")

class ArendStructuralSearchProfile : StructuralSearchProfile() {
    override fun compile(elements: Array<out PsiElement>, globalVisitor: GlobalCompilingVisitor) {
        ArendCompilingVisitor(globalVisitor).compile(elements)
    }

    override fun createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor {
        return ArendMatchingVisitor(globalVisitor)
    }

    override fun getLexicalNodesFilter(): NodeFilter = NodeFilter { element -> element is PsiWhiteSpace }

    override fun createCompiledPattern(): CompiledPattern = object : CompiledPattern() {
        init {
            strategy = ArendMatchingStrategy
        }

        override fun getTypedVarPrefixes(): Array<String> {
            return arrayOf(TYPED_VAR_PREFIX)
        }

        override fun isTypedVar(str: String): Boolean {
            return str.startsWith(TYPED_VAR_PREFIX)
        }

    }

    override fun createPatternTree(
        text: String,
        context: PatternTreeContext,
        fileType: LanguageFileType,
        language: Language,
        contextId: String?,
        project: Project,
        physical: Boolean
    ): Array<PsiElement> {
        val factory = ArendPsiFactory(project)
        if (PROOF_SEARCH_CONTEXT.id == contextId) {
            return try {
                val fragment = factory.createExpression(text)
                arrayOf(fragment)
            } catch (e: Exception) {
                arrayOf(factory.createWhitespace(" ").apply {
                    putUserData(PATTERN_ERROR, ArendBundle.message("arend.only.expressions.are.supported.in.proof.search"))
                })
            }
        }
        return arrayOf(factory.createWhitespace(" ").apply { putUserData(PATTERN_ERROR, ArendBundle.message("arend.only.expressions.are.supported.in.proof.search")) })
    }

    object ArendValidator : ArendVisitor() {
        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            super.visitWhiteSpace(space)
            space.getUserData(PATTERN_ERROR)?.let {
                error -> throw MalformedPatternException(error)
            }
        }
    }

    override fun checkSearchPattern(pattern: CompiledPattern) {
        super.checkSearchPattern(pattern)
        val nodes = pattern.nodes
        while (nodes.hasNext()) {
            nodes.current().accept(ArendValidator)
            nodes.advance()
        }
        nodes.reset()
    }

    override fun getPatternContexts(): List<PatternContext> = PATTERN_CONTEXTS

    override fun getDefaultFileType(fileType: LanguageFileType?): LanguageFileType = fileType ?: ArendFileType

    override fun isMyLanguage(language: Language): Boolean = language === ArendLanguage.INSTANCE

    override fun getTemplateContextTypeClass(): Class<out TemplateContextType> = ArendTemplateContextType::class.java

    override fun isIdentifier(element: PsiElement?): Boolean {
        return element is ArendRefIdentifier
    }
}