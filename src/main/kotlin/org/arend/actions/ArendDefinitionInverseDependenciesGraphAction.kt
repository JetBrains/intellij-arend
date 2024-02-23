package org.arend.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.arend.ArendIcons
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.psi.ext.*

class ArendDefinitionInverseDependenciesGraphAction : AnAction(ArendIcons.ORTHOGONAL_GRAPH) {

    private val usedNodes = mutableSetOf<String>()
    private val edges = mutableSetOf<GraphEdge>()

    private fun findEdge(nameFrom: String, element: PsiElement) {
        element.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is ArendReferenceElement) {
                    val resolve = element.resolve
                    if (resolve is ArendDefinition<*>) {
                        val nameTo = resolve.getName()
                        if (nameTo != null) {
                            edges.add(GraphEdge(nameFrom, nameTo))
                            if (!usedNodes.contains(nameTo)) {
                                findEdges(resolve)
                            }
                        }
                    }
                }
                element.children.forEach { it.accept(this) }
            }
        })
    }

    private fun findEdges(definition: ArendDefinition<*>, optionalNameFrom: String? = null) {
        val nameFrom = optionalNameFrom ?: definition.getName() ?: return
        usedNodes.add(nameFrom)

        for (param in definition.parametersExt) {
            (param.type as? PsiElement?)?.let { findEdge(nameFrom, it) }
        }
        when (definition) {
            is ArendFunctionDefinition -> {
                definition.returnExpr?.let { findEdge(nameFrom, it) }
                definition.body?.let { findEdge(nameFrom, it) }
            }
            is ArendDefClass -> {
                definition.fields.forEach { findEdge(nameFrom, it) }
                if (optionalNameFrom == null) {
                    definition.superClassList.forEach { findEdges(it.longName.resolve as ArendDefinition<*>, nameFrom) }
                } else {
                    definition.superClassList.forEach { findEdges(it.longName.resolve as ArendDefinition<*>, optionalNameFrom) }
                }
            }
            is ArendDefData -> definition.dataBody?.let { findEdge(nameFrom, it) }
            is ArendDefMeta -> definition.expr?.let { findEdge(nameFrom, it) }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        usedNodes.clear()
        edges.clear()

        val definition = getArendDefinition(e) as? ArendDefinition<*> ?: return
        findEdges(definition)

        val graphSimulator = GraphSimulator(this.toString(), edges, usedNodes.map { GraphNode(it) }.toSet())
        graphSimulator.displayOrthogonal()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = getArendDefinition(e) != null
    }

    private fun getArendDefinition(e: AnActionEvent): PsiElement? {
        val editor = e.getData(PlatformDataKeys.EDITOR)
        val psiFile = e.getData(PlatformDataKeys.PSI_FILE)

        if (editor == null || psiFile == null) {
            return null
        }

        val offset = editor.caretModel.offset
        var currentPsiElement = psiFile.findElementAt(offset)
        while (currentPsiElement != null && currentPsiElement !is ArendDefinition<*>) {
            currentPsiElement = currentPsiElement.parent
        }
        return currentPsiElement
    }
}
