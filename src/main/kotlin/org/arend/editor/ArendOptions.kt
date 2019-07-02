package org.arend.editor

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

    var matchingCommentStyle = MatchingCommentStyle.REPLACE_BRACE
    var autoImportOnTheFly = false

    override fun getState() = this

    override fun loadState(state: ArendOptions) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): ArendOptions = ServiceManager.getService(ArendOptions::class.java)
    }
}