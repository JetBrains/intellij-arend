package org.arend.search.structural

import com.intellij.structuralsearch.plugin.ui.filters.FilterAction
import com.intellij.structuralsearch.plugin.ui.filters.FilterProvider

class ArendFilterProvider : FilterProvider {

    override fun getFilters(): List<FilterAction> {
        return emptyList()
    }
}