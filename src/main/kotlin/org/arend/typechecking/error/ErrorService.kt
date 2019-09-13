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

    private fun isValid(file: ArendFile) = file.isWritable

    private fun checkValid(file: ArendFile) = if (isValid(file)) true else {
        nameResolverErrors.remove(file)
        typecheckingErrors.remove(file)
        false
    }

    fun report(error: ArendError) {
        val file = error.file ?: return
        if (checkValid(file)) {
            nameResolverErrors.computeIfAbsent(file) { ArrayList() }.add(error)
        }
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
                if (checkValid(file)) {
                    typecheckingErrors.computeIfAbsent(file) { ArrayList() }.add(ArendError(error, pointer))
                }
            }
        }
    }

    private fun getErrors(map: WeakHashMap<ArendFile, MutableList<ArendError>>, result: HashMap<ArendFile, ArrayList<ArendError>>) {
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (isValid(entry.key)) {
                result.computeIfAbsent(entry.key) { ArrayList() }.addAll(entry.value)
            } else {
                iter.remove()
            }
        }
    }

    val errors: Map<ArendFile, List<ArendError>>
        get() {
            val result = HashMap<ArendFile, ArrayList<ArendError>>()
            getErrors(nameResolverErrors, result)
            getErrors(typecheckingErrors, result)
            return result
        }

    private fun hasErrors(map: WeakHashMap<ArendFile, MutableList<ArendError>>): Boolean {
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (isValid(entry.key)) {
                return true
            } else {
                iter.remove()
            }
        }
        return false
    }

    val hasErrors: Boolean
        get() = hasErrors(nameResolverErrors) || hasErrors(typecheckingErrors)

    fun getErrors(file: ArendFile): List<ArendError> =
        if (checkValid(file)) {
            (nameResolverErrors[file] ?: emptyList<ArendError>()) + (typecheckingErrors[file] ?: emptyList())
        } else {
            emptyList()
        }

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