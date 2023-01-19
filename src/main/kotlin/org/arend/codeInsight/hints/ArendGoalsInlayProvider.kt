package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import org.arend.core.definition.Definition

@Suppress("UnstableApiUsage")
class ArendGoalsInlayProvider : ArendDefinitionInlayProvider() {
    override val key: SettingsKey<NoSettings>
        get() = SettingsKey("arend.inlays.goals")

    override val name: String
        get() = "Goals"

    override val previewText: String
        get() = """
            \func foo => {?}
            
            \func bar => foo
        """.trimIndent()

    override val description
        get() = "Shows definitions with goals used by a definition"

    override fun getText(definition: Definition): String? {
        val goals = HashSet(definition.goals)
        goals.remove(definition)
        return if (goals.isEmpty()) null else "Goals: " + goals.map { it.ref.refLongName }.joinToString()
    }
}