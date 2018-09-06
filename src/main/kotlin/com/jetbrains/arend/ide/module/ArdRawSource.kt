package com.jetbrains.arend.ide.module

import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.jetpad.vclang.source.Source
import com.jetbrains.jetpad.vclang.source.SourceLoader


class ArdRawSource(private val file: ArdFile) : Source {
    override fun getModulePath() = file.modulePath

    override fun load(sourceLoader: SourceLoader) = true

    override fun getTimeStamp() = file.virtualFile?.timeStamp ?: -1

    override fun isAvailable() = file.isValid
}