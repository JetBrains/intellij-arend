package org.arend.actions

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.Processor
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.UIUtil
import org.arend.psi.ArendExpr
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ArendRefIdentifier
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.search.structural.ArendExpressionMatcher
import org.arend.search.structural.PatternTree
import org.arend.search.structural.deconstructArendExpr
import org.arend.term.abs.Abstract
import org.arend.util.arendModules
import javax.swing.JList
import javax.swing.ListCellRenderer

data class ProofSearchEntry(val def : PsiReferable, val tree : PatternTree)

class ArendProofSearchContributor(val event: AnActionEvent) : WeightedSearchEverywhereContributor<ProofSearchEntry> {
    override fun getGroupName(): String = "Proof search"

    override fun getSortWeight(): Int = 201

    override fun isShownInSeparateTab(): Boolean {
        return event.project?.arendModules?.isNotEmpty() ?: false
    }

    override fun getElementsRenderer(): ListCellRenderer<Any> {
        return object : SearchEverywherePsiRenderer(this) {

            override fun customizeNonPsiElementLeftRenderer(
                renderer: ColoredListCellRenderer<*>,
                list: JList<*>,
                value: Any?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ): Boolean {
                val def = value?.castSafelyTo<ProofSearchEntry>()?.def ?: return false
                val patternTree = value.castSafelyTo<ProofSearchEntry>()?.tree ?: return false
                val bgColor = UIUtil.getListBackground()
                val color = list.foreground
                val name = def.name ?: return false
                renderer.append("$name : ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
                val type = def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<PsiElement>() ?: return false
                val toHighlight = mutableListOf<TextRange>()
                type.accept(object : ArendVisitor() {
                    override fun visitRefIdentifier(o: ArendRefIdentifier) {
                        val mname = o.referenceName
                        if (patternTree is PatternTree.BranchingNode && patternTree.subNodes.any { it is PatternTree.LeafNode && it.referenceName.last() == mname }) {
                            toHighlight.add(o.textRange)
                        }
                        super.visitRefIdentifier(o)
                    }
                })
                val plain = SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color)
                if (selected) {
                    renderer.setDynamicSearchMatchHighlighting(true)
                    val highlighted = SimpleTextAttributes(bgColor, color, null, SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SEARCH_MATCH)
                    SpeedSearchUtil.appendColoredFragments(renderer, type.text, listOf(TextRange(0, type.text.length)), plain, highlighted)
                } else {
                    renderer.append(type.text, plain)
                }
                val locationAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                val fm = list.getFontMetrics(list.font)
                val maxWidth = list.width -
                        fm.stringWidth(name) -
                        16 - myRightComponentWidth - 20
                val containerText = getContainerTextForLeftComponent(def, def.name, maxWidth, fm)
                containerText?.run {
                    renderer.append(" $containerText", locationAttributes)
                }
                renderer.icon = this.getIcon(def)
                return true
            }
        }
    }

    override fun getSearchProviderId(): String = ArendProofSearchContributor::class.java.simpleName

    override fun showInFindResults(): Boolean = true

    override fun isDumbAware(): Boolean {
        return false
    }

    override fun processSelectedItem(selected: ProofSearchEntry, modifiers: Int, searchText: String): Boolean {
        // todo: maybe filling selected goal with the proof?
        return true
    }

    override fun getDataForItem(element: ProofSearchEntry, dataId: String): Any? = null

    override fun fetchWeightedElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<ProofSearchEntry>>
    ) {
        val project = event.project ?: return
        runReadAction {
            val parsedExpression = ArendPsiFactory(project).createExpressionMaybe(pattern) ?: return@runReadAction
            val matcher = ArendExpressionMatcher(deconstructArendExpr(parsedExpression))
            val keys = StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, event.project!!)
            for (definitionName in keys) {
                if (progressIndicator.isCanceled) {
                    break
                }
                StubIndex.getInstance().processElements(
                    ArendDefinitionIndex.KEY,
                    definitionName,
                    project,
                    GlobalSearchScope.allScope(project),
                    PsiReferable::class.java
                ) { def ->
                    if (progressIndicator.isCanceled) {
                        return@processElements false
                    }
                    val type = def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<ArendExpr>()
                        ?: return@processElements true
                    if (matcher.match(type)) {
                        // todo: weight
                        consumer.process(FoundItemDescriptor(ProofSearchEntry(def, matcher.tree), 1))
                    }
                    true
                }
            }
        }
    }
}

class ArendProofSearchFactory : SearchEverywhereContributorFactory<ProofSearchEntry> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<ProofSearchEntry> {
        return ArendProofSearchContributor(initEvent)
    }
}