package org.arend.module

import org.arend.source.Source
import org.arend.source.SourceLoader


class ArendFakeRawSource(private val modulePath: ModulePath): Source {
    override fun getModulePath() = modulePath

    override fun load(sourceLoader: SourceLoader) = true

    override fun getTimeStamp(): Long = -1

    override fun isAvailable() = true
}