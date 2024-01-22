package org.arend.module

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.arend.ArendIcons
import org.arend.graph.GraphEdge
import org.arend.graph.GraphSimulator
import org.arend.module.config.ArendModuleConfigService

class ArendModuleDependenciesGraphAction : AnAction(ArendIcons.GRAPH) {
    private val usedNodes = mutableSetOf<Module>()

    private fun findEdges(currentNode: Module, modules: List<Module>): Set<GraphEdge> {
        usedNodes.add(currentNode)

        val from = currentNode.name
        val edges = mutableSetOf<GraphEdge>()

        val arendModuleConfigService = ArendModuleConfigService.getInstance(currentNode) ?: return emptySet()
        val dependencyNames = arendModuleConfigService.dependencies.map { it.name }
        val children = modules.filter { dependencyNames.contains(it.name) }

        for (child in children) {
            edges.add(GraphEdge(from, child.name))

            if (!usedNodes.contains(child)) {
                edges.addAll(findEdges(child, modules))
            }
        }
        return edges
    }

    override fun actionPerformed(e: AnActionEvent) {
        val moduleManager = e.project?.let { ModuleManager.getInstance(it) } ?: return
        val modules = moduleManager.modules.toList()

        usedNodes.clear()
        val edges = mutableSetOf<GraphEdge>()
        for (module in modules) {
            if (!usedNodes.contains(module)) {
                edges.addAll(findEdges(module, modules))
            }
        }

        val simulator = GraphSimulator(this.toString(), edges, usedNodes.map { it.name }.toSet())
        simulator.display()
    }
}
