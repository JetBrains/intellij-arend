package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.naming.reference.DataContainer
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.error.local.LocalError
import java.util.*


class ErrorService : ErrorReporter {
    private val nameResolverErrors = WeakHashMap<ArendFile, MutableList<ArendError>>()
    private val typecheckingErrors = WeakHashMap<ArendFile, MutableList<ArendError>>()

    fun report(error: ArendError) {
        nameResolverErrors.computeIfAbsent(error.file ?: return) { ArrayList() }.add(error)
    }

    override fun report(error: GeneralError) {
        if (!error.isTypecheckingError) {
            return
        }

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
                typecheckingErrors.computeIfAbsent(file) { ArrayList() }.add(ArendError(error, pointer))
            }
        }
    }

    val errors: Map<ArendFile, List<ArendError>>
        get() {
            val result = HashMap<ArendFile, ArrayList<ArendError>>()
            for (entry in nameResolverErrors.entries) {
                result.computeIfAbsent(entry.key) { ArrayList() }.addAll(entry.value)
            }
            for (entry in typecheckingErrors.entries) {
                result.computeIfAbsent(entry.key) { ArrayList() }.addAll(entry.value)
            }
            return result
        }

    fun getErrors(file: ArendFile) =
        (nameResolverErrors[file] ?: emptyList<ArendError>()) + (typecheckingErrors[file] ?: emptyList())

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

    fun updateTypecheckingErrors(file: ArendFile, definition: ArendDefinition?) {
        val arendErrors = typecheckingErrors[file]
        if (arendErrors != null) {
            val it = arendErrors.iterator()
            while (it.hasNext()) {
                val arendError = it.next()
                val errorCause = arendError.cause
                if (errorCause == null || errorCause.ancestor<ArendDefinition>().let { definition != null && definition == it || it == null && arendError.error is LocalError }) {
                    it.remove()
                }
            }
            if (arendErrors.isEmpty()) {
                typecheckingErrors.remove(file)
            }
        }
    }

    fun clearTypecheckingErrors(definition: ArendDefinition) {
        updateTypecheckingErrors(definition.containingFile as? ArendFile ?: return, definition)
    }
}