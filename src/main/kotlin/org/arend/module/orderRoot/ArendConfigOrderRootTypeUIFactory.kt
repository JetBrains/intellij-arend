package org.arend.module.orderRoot

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ui.SdkPathEditor
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory
import org.arend.ArendIcons


class ArendConfigOrderRootTypeUIFactory : OrderRootTypeUIFactory {
    override fun getIcon() = ArendIcons.LIBRARY_CONFIG

    override fun getNodeText() = "Arend Library Config File"

    override fun createPathEditor(sdk: Sdk?): SdkPathEditor? = null
}