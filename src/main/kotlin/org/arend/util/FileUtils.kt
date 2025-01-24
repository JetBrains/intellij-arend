package org.arend.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.fullName
import org.arend.typechecking.TypeCheckingService
import java.lang.StringBuilder

fun StringBuilder.addImports(project: Project, referables: Set<PsiLocatedReferable>): StringBuilder {
    val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
    val definitionsToFiles = mutableSetOf<String>()
    for (referable in referables) {
        val file = referable.containingFile as ArendFile
        if (file == project.service<TypeCheckingService>().prelude) {
            continue
        }
        filesToDefinitions.getOrPut(file) { mutableListOf() }.add(referable.fullName)
        definitionsToFiles.add(referable.fullName)
    }

    val fullFiles = mutableMapOf<ArendFile, Boolean>()
    filesLoop@ for ((file, fileDefinitions) in filesToDefinitions) {
        for (element in LexicalScope.opened(file).elements) {
            val fullName = (element as? PsiLocatedReferable?)?.fullName ?: continue
            if (!fileDefinitions.contains(fullName) && definitionsToFiles.contains(fullName)) {
                fullFiles[file] = false
                continue@filesLoop
            }
            fullFiles[file] = true
        }
    }

    for ((file, importedDefinitions) in filesToDefinitions) {
        if (fullFiles[file] == true) {
            append("\\import ${file.fullName}\n")
        } else {
            append("\\import ${file.fullName}(${importedDefinitions.joinToString(",")})\n")
        }
    }
    if (filesToDefinitions.isNotEmpty()) {
        append("\n")
    }
    return this
}
