package org.arend.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.arend.util.FileUtils


@State(
    name = "ArendSettings",
    storages = [Storage("arend.xml")]
)
class ArendSettings : PersistentStateComponent<ArendSettings> {
    enum class MatchingCommentStyle {
        DO_NOTHING { override fun toString() = "Do nothing" },
        INSERT_MINUS { override fun toString() = "Insert another '-'" },
        REPLACE_BRACE { override fun toString() = "Replace '}' with '-'" }
    }

    var matchingCommentStyle = MatchingCommentStyle.REPLACE_BRACE
    var autoImportOnTheFly = false
    var autoImportWriteOpenCommands = false

    // Background typechecking
    var isBackgroundTypechecking = true

    // Other settings
    var withClauseLimit = true
    var clauseLimit = 10

    var checkForUpdates = true

    var pathToArendJar = ""

    var librariesRoot = FileUtils.defaultLibrariesRoot().toString()

    val clauseActualLimit: Int?
        get() = if (withClauseLimit) clauseLimit else null

    override fun getState() = this

    override fun loadState(state: ArendSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}