package org.arend.settings

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import org.arend.error.GeneralError
import java.util.*

@State(name = "ArendSettings")
class ArendProjectSettings : PersistentStateComponent<ArendProjectSettings> {
    // Hierarchy
    var showImplFields = true
    var showNonImplFields = true
    var hierarchyViewType = TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE!!

    // Messages
    var autoScrollToSource = true
    var autoScrollFromSource = true
    var messagesFilterSet = EnumSet.of(GeneralError.Level.ERROR, GeneralError.Level.WARNING, GeneralError.Level.GOAL)!!

    override fun getState() = this

    override fun loadState(state: ArendProjectSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}