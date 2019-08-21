package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.naming.reference.DataContainer
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import java.util.*


class ErrorService(project: Project) : ErrorReporter {
    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, ErrorService::class.java)!!
    }

    private val nameResolverErrors = WeakHashMap<ArendFile, MutableList<ArendError>>()
    private val typecheckingErrors = WeakHashMap<ArendFile, MutableList<ArendError>>()

    fun report(error: ArendError) {
        val file = error.file ?: return
        if (error.error.isTypecheckingError) {
            typecheckingErrors.computeIfAbsent(file) { ArrayList() }.add(error)
        } else {
            nameResolverErrors.computeIfAbsent(file) { ArrayList() }.add(error)
        }
    }

    override fun report(error: GeneralError) {
        val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
        runReadAction {
            loop@ for (data in list) {
                val element: ArendCompositeElement
                val pointer: SmartPsiElementPointer<*>
                when (val cause = (data as? DataContainer)?.data ?: data) {
                    is ArendCompositeElement -> {
                        element = cause
                        pointer = SmartPointerManager.createPointer(cause)
                    }
                    is SmartPsiElementPointer<*> -> {
                        element = cause.element as? ArendCompositeElement ?: continue@loop
                        pointer = cause
                    }
                    else -> continue@loop
                }

                val file = element.containingFile as? ArendFile ?: continue
                if (error.isTypecheckingError) {
                    typecheckingErrors.computeIfAbsent(file) { ArrayList() }.add(ArendError(error, pointer))
                } else {
                    nameResolverErrors.computeIfAbsent(file) { ArrayList() }.add(ArendError(error, pointer))
                }
            }
        }
    }

    val errors: Map<ArendFile, List<ArendError>>
        get() = nameResolverErrors + typecheckingErrors

    fun getTypecheckingErrors(file: ArendFile): List<Pair<GeneralError, ArendCompositeElement>> {
        val arendErrors = typecheckingErrors[file] ?: return emptyList()

        val list = ArrayList<Pair<GeneralError, ArendCompositeElement>>()
        for (arendError in arendErrors) {
            arendError.cause?.let {
                list.add(Pair(arendError.error, it))
            }
        }
        return list
    }

    fun clearNameResolverErrors(file: ArendFile) {
        nameResolverErrors.remove(file)
    }

    fun clearTypecheckingErrors(definition: ArendDefinition) {
        val file = definition.containingFile as? ArendFile ?: return
        val arendErrors = typecheckingErrors[file]
        if (arendErrors != null) {
            val it = arendErrors.iterator()
            while (it.hasNext()) {
                val arendError = it.next()
                if (definition == arendError.definition) {
                    it.remove()
                }
            }
            if (arendErrors.isEmpty()) {
                typecheckingErrors.remove(file)
            }
        }
    }
}