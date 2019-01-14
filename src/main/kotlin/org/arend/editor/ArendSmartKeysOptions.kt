package org.arend.editor

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil


@State(
    name = "ArendSmartKeysSettings",
    storages = [Storage("editor.codeinsight.xml")]
)
class ArendSmartKeysOptions : PersistentStateComponent<ArendSmartKeysOptions> {
    enum class MatchingCommentStyle {
        DO_NOTHING { override fun toString() = "Do nothing" },
        INSERT_MINUS { override fun toString() = "Insert another '-'" },
        REPLACE_BRACE { override fun toString() = "Replace '}' with '-'" }
    }

    var matchingCommentStyle = MatchingCommentStyle.REPLACE_BRACE

    override fun getState() = this

    override fun loadState(state: ArendSmartKeysOptions) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): ArendSmartKeysOptions = ServiceManager.getService(ArendSmartKeysOptions::class.java)
    }
}