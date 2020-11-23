package org.arend.toolWindow.debugExpr

import org.arend.core.context.binding.Binding
import org.arend.ext.prettyprinting.PrettyPrintable
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import java.lang.StringBuilder

class PrintableBinding(private val binding: Binding) : PrettyPrintable {
    override fun prettyPrint(builder: StringBuilder?, ppConfig: PrettyPrinterConfig?) {
        TODO("Not yet implemented")
    }
}