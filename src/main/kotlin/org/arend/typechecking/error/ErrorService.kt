package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.DataContainer
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.ext.error.LocalError
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.computation.ComputationRunner
import java.util.*


// TODO[server2]: Move this to ArendServer
@Service(Service.Level.PROJECT)
class ErrorService : ErrorReporter {
    private val nameResolverErrors = Collections.synchronizedMap(WeakHashMap<ArendFile, MutableList<ArendError>>())
    private val typecheckingErrors = Collections.synchronizedMap(WeakHashMap<ArendFile, MutableList<ArendError>>())

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
        if (error.stage != GeneralError.Stage.TYPECHECKER) {
            return
        }

        ComputationRunner.checkCanceled()
        val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
        runReadAction {
            loop@ for (data in list) {
                val element: PsiElement
                val pointer: SmartPsiElementPointer<*>
                when (val cause = (data as? DataContainer)?.data ?: data) {
                    is PsiElement -> {
                        if (!cause.isValid) continue@loop
                        element = cause
                        pointer = SmartPointerManager.createPointer(cause)
                    }
                    is SmartPsiElementPointer<*> -> {
                        element = cause.element ?: continue@loop
                        pointer = cause
                    }
                    else -> continue@loop
                }

                val file = element.containingFile as? ArendFile ?: continue
                if (checkValid(file)) {
                    val errorList = typecheckingErrors.computeIfAbsent(file) { ArrayList() }
                    synchronized(errorList) {
                        errorList.add(ArendError(error, pointer))
                    }
                }
            }
        }
    }

    private fun getErrors(map: MutableMap<ArendFile, MutableList<ArendError>>, result: HashMap<ArendFile, ArrayList<ArendError>>) {
        synchronized(map) {
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
    }

    val errors: Map<ArendFile, List<ArendError>>
        get() {
            val result = HashMap<ArendFile, ArrayList<ArendError>>()
            getErrors(nameResolverErrors, result)
            getErrors(typecheckingErrors, result)
            return result
        }

    private fun hasErrors(map: MutableMap<ArendFile, MutableList<ArendError>>): Boolean {
        synchronized(map) {
            val iter = map.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (isValid(entry.key)) {
                    return true
                } else {
                    iter.remove()
                }
            }
        }
        return false
    }

    val hasErrors: Boolean
        get() = hasErrors(nameResolverErrors) || hasErrors(typecheckingErrors)

    fun getErrors(file: ArendFile): List<ArendError> =
        if (checkValid(file)) {
            (nameResolverErrors[file] ?: emptyList()) + (typecheckingErrors[file] ?: emptyList())
        } else {
            emptyList()
        }

    fun getTypecheckingErrors(file: ArendFile): List<Pair<GeneralError, PsiElement>> {
        val arendErrors = typecheckingErrors[file] ?: return emptyList()

        val list = ArrayList<Pair<GeneralError, PsiElement>>()
        synchronized(arendErrors) {
            for (arendError in arendErrors) {
                arendError.cause?.let {
                    list.add(Pair(arendError.error, it))
                }
            }
        }
        return list
    }

    fun clearNameResolverErrors(file: ArendFile) {
        nameResolverErrors.remove(file)
    }

    fun updateTypecheckingErrors(file: ArendFile, definition: PsiLocatedReferable?) {
        val arendErrors = typecheckingErrors[file]
        if (arendErrors != null) {
            synchronized(arendErrors) {
                val it = arendErrors.iterator()
                while (it.hasNext()) {
                    val arendError = it.next()
                    val errorCause = arendError.cause
                    if (errorCause == null || errorCause.ancestor<PsiLocatedReferable>().let { definition != null && definition == it || it == null && arendError.error is LocalError }) {
                        it.remove()
                    }
                }
                if (arendErrors.isEmpty()) {
                    typecheckingErrors.remove(file)
                }
            }
        }
    }

    fun clearTypecheckingErrors(definition: PsiLocatedReferable) {
        updateTypecheckingErrors(definition.containingFile as? ArendFile ?: return, definition)
    }

    fun clearTypecheckingErrors(file: ArendFile) {
        typecheckingErrors.remove(file)
    }

    fun clearAllErrors() {
        nameResolverErrors.clear()
        typecheckingErrors.clear()
    }
}