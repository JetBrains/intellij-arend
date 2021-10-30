package org.arend.search.structural

import com.intellij.codeInsight.template.FileTypeBasedContextType
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.util.NlsContexts
import org.arend.ArendFileType
import org.arend.psi.ArendFile
import org.jetbrains.annotations.NonNls

class ArendTemplateContextType : FileTypeBasedContextType("arend-template", "Arend", ArendFileType)