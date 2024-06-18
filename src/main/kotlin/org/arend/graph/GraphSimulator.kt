package org.arend.graph

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.ui.components.JBPanel
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import java.awt.Color
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.ActionEvent
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
            val image = graphviz.render(Format.PNG).toImage()
            val imageIcon = ImageIcon(image)

            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val screenWidth = screenSize.getWidth().toInt()
            val screenHeight = screenSize.getHeight().toInt()

            val baseImageIcon = if (imageIcon.iconWidth > screenWidth && imageIcon.iconHeight > screenHeight) {
                ImageIcon(graphviz.width(screenWidth).height(screenHeight).render(Format.PNG).toImage())
            } else if (imageIcon.iconWidth > screenWidth) {
                ImageIcon(graphviz.width(screenWidth).height(imageIcon.iconHeight).render(Format.PNG).toImage())
            } else if (imageIcon.iconWidth > screenWidth) {
                ImageIcon(graphviz.width(imageIcon.iconWidth).height(screenHeight).render(Format.PNG).toImage())
            } else {
                imageIcon
            }

            var imageLabel = JLabel(baseImageIcon)

            val dialogWrapper = object : DialogWrapper(project, true) {
                private var graphImage = image
                private val copyImageAction = object : DialogWrapperAction("Copy Image") {
                    override fun doAction(e: ActionEvent?) {
                        val transferableImage = TransferableImage(graphImage)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferableImage, null)
                        buttonMap[this]?.isEnabled = false
                    }
                }

                private val copyFullImageAction = object : DialogWrapperAction("Copy Full Image") {
                    override fun doAction(e: ActionEvent?) {
                        val transferableImage = TransferableImage(image)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferableImage, null)
                    }
                }

                init {
                    init()
                }

                override fun createCenterPanel(): JComponent {
                    val panel = JBPanel<JBPanel<*>>()
                    panel.add(imageLabel)
                    panel.background = Color.WHITE
                    panel.addComponentListener(object : ComponentAdapter() {
                        override fun componentResized(e: ComponentEvent) {
                            val component = panel.components.getOrNull(0) ?: return
                            panel.withMinimumWidth(baseImageIcon.iconWidth + size.width - e.component.width)
                            panel.withMinimumHeight(baseImageIcon.iconHeight + size.height - e.component.height)

                            val newImageIcon = getImageIcon(baseImageIcon, graphviz, e.component.width - component.bounds.x * 2, e.component.height - component.bounds.y * 2)
                            imageLabel = JLabel(newImageIcon)

                            panel.removeAll()
                            panel.add(imageLabel)
                            panel.revalidate()
                            buttonMap[copyImageAction]?.isEnabled = true
                        }
                    })
                    return panel
                }

                private fun getImageIcon(baseImageIcon: ImageIcon, graphviz: Graphviz, width: Int, height: Int): ImageIcon {
                    return when {
                        width <= baseImageIcon.iconWidth && height <= baseImageIcon.iconHeight -> {
                            graphImage = image
                            baseImageIcon
                        }
                        width <= baseImageIcon.iconWidth -> {
                            graphImage = graphviz.width(baseImageIcon.iconWidth).height(height).render(Format.PNG).toImage()
                            ImageIcon(graphImage)
                        }
                        height <= baseImageIcon.iconHeight -> {
                            graphImage =graphviz.width(width).height(baseImageIcon.iconHeight).render(Format.PNG).toImage()
                            ImageIcon(graphImage)
                        }
                        else -> {
                            graphImage = graphviz.width(width).height(height).render(Format.PNG).toImage()
                            ImageIcon(graphImage)
                        }
                    }
                }

                override fun createActions(): Array<Action> {
                    return arrayOf(copyImageAction, copyFullImageAction)
                }
            }
            dialogWrapper.pack()
            dialogWrapper.show()
        } catch (e: Exception) {
            val notification = notificationGroup.createNotification("It is not possible to visualize the diagram on your device. Install Graphviz please", MessageType.WARNING)
                .addAction(NotificationAction.createSimple("Open Graphviz") {
                    BrowserUtil.open("https://graphviz.org/download/")
                })
            Notifications.Bus.notify(notification, project)
        }
    }

    companion object {
        class TransferableImage(private val image: Image) : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> {
                return arrayOf(DataFlavor.imageFlavor)
            }

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
                return flavor.equals(DataFlavor.imageFlavor)
            }

            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor.equals(DataFlavor.imageFlavor)) {
                    return image
                }
                throw UnsupportedFlavorException(flavor)
            }
        }
    }
}
