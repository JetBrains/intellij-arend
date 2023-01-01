package org.arend.ui.impl.session

import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.extImpl.ui.DelegateQuery
import org.arend.extImpl.ui.SimpleQuery
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.resolving.ArendReferenceBase
import org.arend.typechecking.TypeCheckingService
import org.arend.ui.ArendDialog

class ArendEditorSession(private val project: Project, private val editor: Editor) : ComponentSession() {
    private var singleListRequest: ListRequest<Any>? = null

    private class ListRequest<T : Any>(val message: String?, val list: List<T>, val default: T?) {
        val query = DelegateQuery<T>()
    }

    override val allItems: List<ComponentSessionItem>
        get() {
            mergeSingleListRequest()
            return super.allItems
        }

    private fun mergeSingleListRequest() {
        val singleListRequest = singleListRequest
        if (singleListRequest != null) {
            singleListRequest.query.setDelegate(comboBoxQuery(0, singleListRequest.message, singleListRequest.list, singleListRequest.default))
            this.singleListRequest = null
        }
    }

    override fun <T : Any> listQuery(message: String?, options: List<T>, defaultOption: T?) =
        if (singleListRequest == null && items.isEmpty() && options.isNotEmpty()) {
            val request = ListRequest(message, options, defaultOption)
            val query = request.query
            @Suppress("UNCHECKED_CAST")
            singleListRequest = request as ListRequest<Any>
            query
        } else {
            mergeSingleListRequest()
            super.listQuery(message, options, defaultOption)
        }

    private fun <T : Any> showPopup(request: ListRequest<T>) {
        val query = SimpleQuery<T>()
        request.query.setDelegate(query)

        val service = project.service<TypeCheckingService>()
        val lookupList = request.list.map { elem ->
            val ref = (elem as? LocatedReferable)?.let { service.getPsiReferable(it) } ?: if (elem is Definition) service.getDefinitionPsiReferable(elem) else elem as? Referable
            val element = (if (ref != null) ArendReferenceBase.createArendLookUpElement(ref, null, false, null, false, "")?.withInsertHandler { _, _ -> } else null)
                ?: LookupElementBuilder.create(elem, "")
            element.withPresentableText(ref?.refName ?: elem.toString())
        }
        val lookup = LookupManager.getInstance(project).showLookup(editor, *lookupList.toTypedArray())
        lookup?.addLookupListener(object : LookupListener {
            override fun itemSelected(event: LookupEvent) {
                query.result = request.list.getOrNull(lookupList.indexOf(event.item))
                endSession(true)
            }

            override fun lookupCanceled(event: LookupEvent) {
                endSession(false)
            }
        })
        if (request.message != null) {
            (lookup as? LookupImpl)?.addAdvertisement(request.message, null)
        }
    }

    override fun doStart() {
        val singleListRequest = singleListRequest
        if (singleListRequest != null) {
            if (items.isEmpty()) {
                showPopup(singleListRequest)
                return
            } else {
                mergeSingleListRequest()
            }
        }

        endSession(ArendDialog(project, description, null, component, focused).showAndGet())
    }
}