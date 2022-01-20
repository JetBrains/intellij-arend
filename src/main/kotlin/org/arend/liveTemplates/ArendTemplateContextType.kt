package org.arend.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import org.arend.util.FileUtils

class ArendTemplateContextType : TemplateContextType("AREND", "Arend") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file.name.endsWith(FileUtils.EXTENSION)
    }
}