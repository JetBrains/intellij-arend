package org.arend.module.orderRoot

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.roots.libraries.ui.RootDetector
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.FileUtils


object ArendConfigRootDetector : RootDetector(ArendConfigOrderRootType.INSTANCE, false, "Arend Library Config File") {
    override fun detectRoots(rootCandidate: VirtualFile, progressIndicator: ProgressIndicator): List<VirtualFile> =
        if (rootCandidate.isDirectory) {
            rootCandidate.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let { listOf(it) } ?: emptyList()
        } else {
            if (rootCandidate.name == FileUtils.LIBRARY_CONFIG_FILE) listOf(rootCandidate)
            else JarFileSystem.getInstance().getJarRootForLocalFile(rootCandidate)?.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let { listOf(it) } ?: emptyList()
        }
}