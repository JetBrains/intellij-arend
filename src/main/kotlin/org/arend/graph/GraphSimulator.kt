package org.arend.graph

import org.graphstream.graph.Graph
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.ui.view.Viewer

data class GraphEdge(val from: String, val to: String, val isDirected: Boolean)

class SingleGraphSimulator(private val id: String, private val edges: Set<GraphEdge>, private val vertices: Set<String>) {
    private val styleSheet = "node {" +
            "   shape: box;" +
            "   text-background-mode: plain;" +
            "	fill-color: white;" +
            "   text-size: 30px;" +
            "}" +
            "edge {" +
            "   size: 5px;" +
            "   shape: angle;" +
            "   arrow-size: 30px, 5px;" +
            "}"

    fun display() {
        System.setProperty("org.graphstream.ui", "swing")

        val graph: Graph = SingleGraph(id)
        graph.setAttribute("ui.stylesheet", styleSheet)
        graph.setAutoCreate(true)
        graph.isStrict = false

        val view = graph.display()
        view.closeFramePolicy = Viewer.CloseFramePolicy.CLOSE_VIEWER

        for (vertex in vertices) {
            graph.addNode(vertex)
        }

        for ((index, edge) in edges.withIndex()) {
            graph.addEdge(index.toString(), edge.from, edge.to, edge.isDirected)
        }

        for (node in graph) {
            node.setAttribute("ui.label", node.id)
        }
    }
}
