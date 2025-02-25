package org.arend.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.arend.ArendIcons.MUTUAL_RECURSIVE
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.elimtree.ElimClause
import org.arend.core.pattern.ExpressionPattern
import org.arend.error.DummyErrorReporter
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.search.ClassDescendantsSearch
import org.arend.term.concrete.Concrete
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.local.LocalErrorReporter
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.instance.provider.SimpleInstanceProvider
import org.arend.typechecking.patternmatching.ExtElimClause
import org.arend.typechecking.termination.BaseCallMatrix
import org.arend.typechecking.termination.DefinitionCallGraph
import org.arend.typechecking.visitor.CheckTypeVisitor
import org.arend.typechecking.visitor.DefinitionTypechecker
import org.arend.util.ArendBundle
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ArendLineMarkerProvider: LineMarkerProviderDescriptor() {
    private val url = "https://www.cse.chalmers.se/~abela/foetus.pdf"

    override fun getName() = "Arend line markers"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        subclassesLineMarkers(elements, result)
        recursiveLineMarkers(elements, result)
    }

    private fun subclassesLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            if (element is ArendDefIdentifier) {
                (element.parent as? ArendDefClass)?.let { clazz ->
                    ProgressManager.checkCanceled()
                    if (clazz.project.service<ClassDescendantsSearch>().search(clazz).isNotEmpty()) {
                        result.add(LineMarkerInfo(element.id, element.textRange, AllIcons.Gutter.OverridenMethod,
                            SUPERCLASS_OF.tooltip, SUPERCLASS_OF.navigationHandler,
                            GutterIconRenderer.Alignment.RIGHT) { "subclasses" })
                    }
                }
            }
        }
    }

    private fun recursiveLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile as? ArendFile ?: return
        val concreteDefs = mutableMapOf<FunctionDefinition, Concrete.Definition>()
        val defsClauses = mutableMapOf<FunctionDefinition, List<ElimClause<ExpressionPattern>>>()
        val otherDefs = mutableSetOf<ArendDefFunction>()
        val defsToPsiElement = mutableMapOf<FunctionDefinition, ArendDefFunction>()

        val project = file.project
        val definitions = file.concreteDefinitions
        for (definition in definitions) {
            ProgressManager.checkCanceled()
            val concrete = definition.value as? Concrete.Definition? ?: continue
            val defReferable = concrete.data
            val typechecked = defReferable.typechecked as? FunctionDefinition? ?: continue
            val element = defReferable.underlyingReferable as? ArendDefFunction? ?: continue
            if (!elements.contains(element)) continue

            val clauses = getClauses(project, defReferable, concrete) ?: emptyList()

            if (concrete.isRecursive) {
                concreteDefs.putIfAbsent(typechecked, concrete)
                defsClauses.putIfAbsent(typechecked, clauses)
                defsToPsiElement[typechecked] = element

                otherDefs.addAll(PsiTreeUtil.findChildrenOfType(element, ArendRefIdentifier::class.java)
                    .map { it.resolve }.filterIsInstance<ArendDefFunction>().filter { it.containingFile != file })
            }
        }

        addOtherDefinitions(otherDefs, concreteDefs, defsClauses)

        val graph = getCallGraph(concreteDefs, defsClauses)
        for (vertex in graph.graph.entries) {
            val element = defsToPsiElement[vertex.key] ?: continue
            val otherVertexes = vertex.value
            var isMutualRecursive = false
            for (otherVertex in otherVertexes) {
                if (otherVertex.key == vertex.key) continue
                if (graph.graph[otherVertex.key]?.contains(vertex.key) == true) {
                    isMutualRecursive = true
                    break
                }
            }
            if (isMutualRecursive) {
                result.add(LineMarkerInfo(element, element.textRange, MUTUAL_RECURSIVE,
                    { "Show the call graph" }, { _, _ -> mutualRecursiveCall(project, graph, vertex) },
                    GutterIconRenderer.Alignment.CENTER) { "callGraph" })
            } else {
                result.add(LineMarkerInfo(element, element.textRange, AllIcons.Gutter.RecursiveMethod,
                    { "Show the call matrix" }, { _, _ -> selfRecursiveCall(vertex) },
                    GutterIconRenderer.Alignment.CENTER) { "callMatrix" })
            }
        }
    }

    private fun getClauses(project: Project, defReferable: TCDefReferable, concrete: Concrete.Definition): MutableList<ExtElimClause>? {
        val extension = LibraryArendExtensionProvider(project.service<TypeCheckingService>().libraryManager).getArendExtension(defReferable)
        val checkTypeVisitor =
            CheckTypeVisitor(LocalErrorReporter(defReferable, DummyErrorReporter.INSTANCE), GlobalInstancePool(SimpleInstanceProvider(listOf(defReferable)), null), extension)
        val typechecker = DefinitionTypechecker(checkTypeVisitor, setOf(defReferable))
        return concrete.accept(typechecker, null)
    }

    private fun addOtherDefinitions(
        definitions: Set<ArendDefFunction>,
        concreteDefs: MutableMap<FunctionDefinition, Concrete.Definition>,
        defsClauses: MutableMap<FunctionDefinition, List<ElimClause<ExpressionPattern>>>
    ) {
        val project = definitions.firstOrNull()?.project ?: return
        val files = definitions.mapNotNull { it.containingFile as? ArendFile? }
        ArendTypechecking.create(project).typecheckModules(files, null)

        for (definition in definitions) {
            val file = definition.containingFile as? ArendFile? ?: continue
            val defReferable = file.getTCRefMap(Referable.RefKind.EXPR).values.find {
                it.underlyingReferable == definition
            } as? DataLocatedReferable? ?: continue
            val typechecked = defReferable.typechecked as? FunctionDefinition? ?: continue
            val provider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true)
            val concrete = provider.getConcrete(defReferable) as? Concrete.Definition? ?: continue

            val clauses = getClauses(project, defReferable, concrete) ?: emptyList()
            concreteDefs.putIfAbsent(typechecked, concrete)
            defsClauses.putIfAbsent(typechecked, clauses)
        }
    }

    private fun toTextOfCallMatrix(vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>): String {
        val result = StringBuilder()
        for ((otherVertex, matrices) in vertex.value.entries) {
            if (otherVertex != vertex.key) continue
            result.append(vertex.key.name).append(" -> ").append(otherVertex.name).append("\n ")
            for (matrix in matrices) {
                result.append(matrix.toString()).append("\n")
            }
        }
        return result.toString()
    }

    private fun selfRecursiveCall(vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>) {
        val title = ArendBundle.message("arend.termination.checker.recursive")
        val info = ArendBundle.message("arend.termination.checker.info")
        val matrices = ArendBundle.message("arend.termination.checker.show.matrices")
        val balloon = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(title, listOf(info, matrices)) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == info) {
                    BrowserUtil.browse(url)
                } else if (selectedValue == matrices) {
                    doFinalStep {
                        JBPopupFactory.getInstance()
                            .createBalloonBuilder(JScrollPane(JTextArea().apply {
                                text = toTextOfCallMatrix(vertex)
                            }))
                            .setHideOnClickOutside(true)
                            .createBalloon()
                            .show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location), Balloon.Position.atRight)
                    }
                }
                return FINAL_CHOICE
            }
        })
        balloon.show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location))
    }

    private fun getNameDefinition(vertex: Definition): String? {
        return (vertex.referable.underlyingReferable as? ArendDefFunction?)?.fullName
    }

    private fun mutualRecursiveCall(project: Project, graph: DefinitionCallGraph, vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>) {
        val title = ArendBundle.message("arend.termination.checker.recursive")
        val info = ArendBundle.message("arend.termination.checker.info")
        val callGraph = ArendBundle.message("arend.termination.checker.show.graph")
        val balloon = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(title, listOf(info, callGraph)) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == info) {
                    BrowserUtil.browse(url)
                } else if (selectedValue == callGraph) {
                    val vertexes = (mutableListOf(vertex.key) + vertex.value.keys)
                    val edges = mutableListOf<GraphEdge>()
                    for ((otherVertex, edgesToOtherVertex) in vertex.value) {
                        for (edge in edgesToOtherVertex) {
                            val from = getNameDefinition(vertex.key) ?: continue
                            val to = getNameDefinition(otherVertex) ?: continue
                            edges.add(GraphEdge(from, to, edge.toString()))
                        }
                    }
                    for (otherVertex in vertex.value.keys) {
                        val otherEdges = graph.graph[otherVertex] ?: continue
                        for ((newVertex, edgesFromOtherVertex) in otherEdges) {
                            if (vertexes.contains(newVertex)) {
                                for (edge in edgesFromOtherVertex) {
                                    val from = getNameDefinition(otherVertex) ?: continue
                                    val to = getNameDefinition(newVertex) ?: continue
                                    edges.add(GraphEdge(from, to, edge.toString()))
                                }
                            }
                        }
                    }
                    doFinalStep {
                        project.service<GraphSimulator>().displayOrthogonal(
                            this.toString(),
                            "Graph_Call_From_${vertex.key.name}",
                            edges.toSet(),
                            vertexes.mapNotNull { (it.referable.underlyingReferable as? ArendDefFunction?)?.fullName }.map { GraphNode(it) }.toSet()
                        )
                    }
                }
                return FINAL_CHOICE
            }
        })
        balloon.show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location))
    }

    private fun getCallGraph(
        definitions: Map<FunctionDefinition, Concrete.Definition>,
        clauses: Map<FunctionDefinition, List<ElimClause<ExpressionPattern>>>
    ): DefinitionCallGraph {
        val definitionCallGraph = DefinitionCallGraph()
        for ((key) in definitions) {
            val functionClauses = clauses[key]
            definitionCallGraph.add(key, functionClauses ?: emptyList(), definitions.keys)
        }
        return definitionCallGraph
    }

    companion object {
        private val SUPERCLASS_OF = MarkerType("SUPERCLASS_OF", { "Is overridden by several subclasses" },
            object : LineMarkerNavigator() {
                override fun browse(e: MouseEvent, element: PsiElement) {
                    val clazz = element.parent.parent as? ArendDefClass ?: return
                    PsiTargetNavigator(clazz.project.service<ClassDescendantsSearch>().getAllDescendants(clazz).toTypedArray()).navigate(e, "Subclasses of " + clazz.name, element.project)
                }
            })
    }
}
