package org.arend.ui.impl

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.ui.ArendConsole
import org.arend.naming.scope.Scopes
import org.arend.psi.ext.ArendCompositeElement
import org.arend.settings.ArendProjectSettings
import org.arend.ui.console.ArendConsoleService

class ArendConsoleImpl(private val project: Project, marker: Any?) : ArendConsole {
    private val scopes = if (marker is ArendCompositeElement) runReadAction { marker.scopes.caching() } else Scopes.EMPTY

    override fun println(doc: Doc) {
        project.service<ArendConsoleService>().print(doc, scopes)
    }

    override fun getPrettyPrinterConfig() =
        object : PrettyPrinterConfig {
            override fun getExpressionFlags() = project.service<ArendProjectSettings>().consolePrintingOptionsFilterSet

            override fun getNormalizationMode() = NormalizationMode.ENF
        }
}