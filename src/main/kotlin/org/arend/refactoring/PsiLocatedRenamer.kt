package org.arend.refactoring

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import org.arend.ext.module.LongName
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.reference.ArendRef
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.TypeCheckingService

class PsiLocatedRenamer(private val element: ArendCompositeElement, private val file: ArendFile = element.containingFile as ArendFile) : DefinitionRenamer {
    private val service = file.project.service<TypeCheckingService>()
    private val deferredNsCmdActions = ArrayList<NsCmdRefactoringAction>()

    override fun renameDefinition(arendRef: ArendRef): LongName? = runReadAction {
        val ref = (arendRef as? LocatedReferable)?.let { service.getPsiReferable(it) } ?: return@runReadAction null
        val pair = calculateReferenceName(LocationData(ref), file, element, false, deferredNsCmdActions) ?: return@runReadAction null
        val action = pair.first
        if (action != null) deferredNsCmdActions.add(action)
        LongName(pair.second)
    }

    fun writeAllImportCommands() {
        for (l in deferredNsCmdActions) l.execute()
        deferredNsCmdActions.clear()
    }
}