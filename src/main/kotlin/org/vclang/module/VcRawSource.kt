package org.vclang.module

import com.jetbrains.jetpad.vclang.source.Source
import com.jetbrains.jetpad.vclang.source.SourceLoader
import org.vclang.psi.VcFile


class VcRawSource(private val file: VcFile): Source {
    override fun getModulePath() = file.modulePath

    override fun load(sourceLoader: SourceLoader) = true

    override fun getTimeStamp() = file.virtualFile?.timeStamp ?: -1

    override fun isAvailable() = file.isValid
}