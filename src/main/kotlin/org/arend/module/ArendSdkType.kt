package org.arend.module

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.projectRoots.*
import org.jdom.Element

class ArendSdkType: SdkType("ArendSdkType") {
    override fun getPresentableName() = "Arend libraries"

    override fun isValidSdkHome(path: String) = true

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String?) = currentSdkName ?: presentableName

    override fun suggestHomePath(): String? = null

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun getHomeChooserDescriptor(): FileChooserDescriptor =
        FileChooserDescriptor(false, true, false, false, false, false)

    override fun getVersionString(sdkHome: String?) = "1.0"
}