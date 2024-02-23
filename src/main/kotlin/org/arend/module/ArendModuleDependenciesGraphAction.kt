package org.arend.module

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.arend.ArendIcons
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.module.config.ArendModuleConfigService

class ArendModuleDependenciesGraphAction : AnAction(ArendIcons.ORTHOGONAL_GRAPH) {
    private val usedNodes = mutableSetOf<Module>()
    private val edges = mutableSetOf<GraphEdge>()

    private fun findEdges(currentNode: Module, modules: List<Module>) {
        usedNodes.add(currentNode)

        val from = currentNode.name
        val edges = mutableSetOf<GraphEdge>()

        val arendModuleConfigService = ArendModuleConfigService.getInstance(currentNode) ?: return
        val dependencyNames = arendModuleConfigService.dependencies.map { it.name }
        val children = modules.filter { dependencyNames.contains(it.name) }

        for (child in children) {
            edges.add(GraphEdge(from, child.name))

            if (!usedNodes.contains(child)) {
                findEdges(child, modules)
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        usedNodes.clear()
        edges.clear()

        val moduleManager = e.project?.let { ModuleManager.getInstance(it) } ?: return
        val modules = moduleManager.modules.toList()
        for (module in modules) {
            if (!usedNodes.contains(module)) {
                findEdges(module, modules)
            }
        }

        val simulator = GraphSimulator(this.toString(), edges, usedNodes.map { GraphNode(it.name) }.toSet())
        simulator.displayOrthogonal()
    }
}
