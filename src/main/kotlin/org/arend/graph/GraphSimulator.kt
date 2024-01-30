package org.arend.graph

import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import java.awt.Toolkit
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

data class GraphEdge(val from: String, val to: String)

class GraphSimulator(
    private val graphId: String, private val edges: Set<GraphEdge>, private val vertices: Set<String>
) {

    fun display() {
        var graph = graph(graphId).directed()

        for (vertex in vertices) {
            graph = graph.with(node(vertex))
        }

        for (edge in edges) {
            graph = graph.with(node(edge.from).link(node(edge.to)))
        }

        val graphviz = Graphviz.fromGraph(graph)
        val baseImageIcon = ImageIcon(
            graphviz.render(Format.PNG).toImage()
        )

        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val screenWidth = (screenSize.getWidth() * FULL_SCREEN_CALIBRATION_WIDTH_FACTOR).toInt()
        val screenHeight = (screenSize.getHeight() * FULL_SCREEN_CALIBRATION_HEIGHT_FACTOR).toInt()
        var isResized = false

        val imageIcon = when {
            screenWidth <= baseImageIcon.iconWidth || screenHeight <= baseImageIcon.iconHeight -> {
                isResized = true
                ImageIcon(
                    graphviz.width(screenWidth).height(screenHeight).render(Format.PNG).toImage()
                )
            }
            else -> baseImageIcon
        }

        val imageLabel = JLabel(imageIcon)

        val frame = JFrame()
        frame.contentPane.add(imageLabel)
        frame.pack()
        if (isResized) {
            frame.extendedState = JFrame.MAXIMIZED_BOTH
        }
        frame.isVisible = true
    }

    companion object {
        const val FULL_SCREEN_CALIBRATION_WIDTH_FACTOR = 0.965
        const val FULL_SCREEN_CALIBRATION_HEIGHT_FACTOR = 0.95
    }
}
