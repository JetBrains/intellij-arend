package org.arend.graph

import com.vladsch.flexmark.util.html.ui.Color
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
        val screenWidth = screenSize.getWidth().toInt()
        val screenHeight = screenSize.getHeight().toInt()

        var imageIcon = getImageIcon(baseImageIcon, graphviz, screenWidth, screenHeight)
        var imageLabel = JLabel(imageIcon)

        val frame = JFrame()
        frame.contentPane.add(imageLabel)
        frame.contentPane.background = Color.WHITE
        frame.pack()
        frame.setLocation((screenWidth - frame.width) / 2, (screenHeight - frame.height) / 2)
        frame.isVisible = true

        frame.contentPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                imageIcon = getImageIcon(baseImageIcon, graphviz, e.component.width, e.component.height)
                imageLabel = JLabel(imageIcon)

                frame.contentPane.removeAll()
                frame.contentPane.add(imageLabel)
                frame.revalidate()
            }
        })
    }

    private fun getImageIcon(baseImageIcon: ImageIcon, graphviz: Graphviz, width: Int, height: Int): ImageIcon {
        return when {
            width <= baseImageIcon.iconWidth || height <= baseImageIcon.iconHeight -> {
                ImageIcon(
                    graphviz.width(width).height(height).render(Format.PNG).toImage()
                )
            }
            else -> baseImageIcon
        }
    }
}
