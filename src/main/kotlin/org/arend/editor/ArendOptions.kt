package org.arend.editor

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil


@State(
    name = "ArendSettings",
    storages = [Storage("editor.codeinsight.xml")]
)
class ArendOptions : PersistentStateComponent<ArendOptions> {
    enum class MatchingCommentStyle {
        DO_NOTHING { override fun toString() = "Do nothing" },
        INSERT_MINUS { override fun toString() = "Insert another '-'" },
        REPLACE_BRACE { override fun toString() = "Replace '}' with '-'" }
    }

    enum class TypecheckingMode {
        SMART { override fun toString() = "Smart" },
        DUMB { override fun toString() = "Dumb" },
        OFF { override fun toString() = "Off" }
    }

    var matchingCommentStyle = MatchingCommentStyle.REPLACE_BRACE
    var autoImportOnTheFly = false
    var typecheckingMode = TypecheckingMode.SMART
    var withTimeLimit = true
    var typecheckingTimeLimit = 5

    // Hierarchy
    var showImplFields: Boolean = true
    var showNonimplFields: Boolean = true
    var hierarchyViewType: String = TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE

    override fun getState() = this

    override fun loadState(state: ArendOptions) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: ArendOptions = ServiceManager.getService(ArendOptions::class.java)
    }
}