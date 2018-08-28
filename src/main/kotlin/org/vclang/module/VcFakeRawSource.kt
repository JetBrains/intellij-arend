package org.vclang.module

import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.source.Source
import com.jetbrains.jetpad.vclang.source.SourceLoader


class VcFakeRawSource(private val modulePath: ModulePath): Source {
    override fun getModulePath() = modulePath

    override fun load(sourceLoader: SourceLoader) = true

    override fun getTimeStamp(): Long = -1

    override fun isAvailable() = true
}