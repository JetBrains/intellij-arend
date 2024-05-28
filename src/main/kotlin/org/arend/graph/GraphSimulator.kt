package org.arend.graph

import com.intellij.notification.NotificationListener
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import java.awt.Color
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

data class GraphNode(val id: String)

data class GraphEdge(val from: String, val to: String)

class GraphSimulator(
    private val project: Project?,
    private val graphId: String,
    private val edges: Set<GraphEdge>,
    private val vertices: Set<GraphNode>
) {

    fun displayOrthogonal() {
        try {
            var graph = graph(graphId).directed()

            for (vertex in vertices) {
                graph = graph.with(node(vertex.id))
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

            val dialogWrapper = object : DialogWrapper(project, true) {
                init {
                    init()
                }

                override fun createCenterPanel(): JComponent {
                    val panel = JPanel()
                    panel.add(imageLabel)
                    panel.background = Color.WHITE
                    panel.addComponentListener(object : ComponentAdapter() {
                        override fun componentResized(e: ComponentEvent) {
                            imageIcon = getImageIcon(baseImageIcon, graphviz, e.component.width, e.component.height)
                            imageLabel = JLabel(imageIcon)

                            panel.removeAll()
                            panel.add(imageLabel)
                            panel.revalidate()
                        }
                    })
                    return panel
                }

                override fun createActions(): Array<Action> = emptyArray()
            }
            dialogWrapper.pack()
            dialogWrapper.show()
        } catch (e: Exception) {
            val notification = notificationGroup.createNotification("It is not possible to visualize the diagram on your device. Install <a href=https://graphviz.org/download/>Graphviz</a> please", MessageType.WARNING)
            notification.setListener(NotificationListener.UrlOpeningListener(true))
            Notifications.Bus.notify(notification, project)
        }
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
