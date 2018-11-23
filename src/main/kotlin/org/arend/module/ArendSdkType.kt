package org.arend.module

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.projectRoots.*
import org.jdom.Element

class ArendSdkType: SdkType("ArendSdkType") {
    override fun getPresentableName(): String = "Arend libraries"

    override fun isValidSdkHome(path: String): Boolean = true

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String?): String = currentSdkName ?: presentableName

    override fun suggestHomePath(): String? = null

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun getHomeChooserDescriptor(): FileChooserDescriptor =
        FileChooserDescriptor(false, true, false, false, false, false)

    override fun getVersionString(sdkHome: String?): String? {
        return "1.0"
    }

    /* In order to make sourcesDir and outputDir visible in corresponding places, presumably,
    the method below should be implemented accordingly. The current code makes no effect whatsoever.

    override fun setupSdkPaths(sdk: Sdk) {
        val homePath = sdk.homePath ?: error(sdk)
        val jdkHome = File(homePath)
        val sdkModificator = sdk.sdkModificator

        for (lib in findAllLibrariesInDirectory(jdkHome.toPath())) {
            sdkModificator.addRoot(lib.toString(), OrderRootType.CLASSES)
        }

        sdkModificator.commitChanges()
    }
    */
}