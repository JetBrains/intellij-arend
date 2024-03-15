package org.arend.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes.FAT_ARROW
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.term.abs.Abstract
import java.util.*

class ParameterDescriptor private constructor(
    val isExplicit: Boolean,
    val type: String?,
    val name: String?,
    val isDataParameter: Boolean,
    private val externalScope: SmartPsiElementPointer<ArendGroup>? = null,
    private val psiReferable: SmartPsiElementPointer<PsiElement>? = null,
    private val envelopingClass: SmartPsiElementPointer<ArendDefClass>? = null,
    val oldParameter: ParameterDescriptor? = null
) {
    init {
        if (externalScope != null) when (externalScope.element) {
            is ArendDefFunction, is ArendDefData, is ArendDefInstance/*, is ArendDefModule */-> {}
            else -> throw IllegalArgumentException()
        }
    }

    constructor(isExplicit: Boolean, type: String?, name: String?): this(isExplicit, type, name, false)
    constructor (referable: Referable, isExplicit: Boolean = computeExplicitness(referable as PsiElement), type: String? = computeType(referable as PsiElement)):
            this(isExplicit, type, referable.refName, false, null,
                if (referable is PsiElement) createSmartLink(referable) else null)

    override fun toString(): String = "${if (isExplicit) "(" else "{"}${getNameOrUnderscore()}${if (getType1() != null) " : " + getType1() else ""}${if (isExplicit) ")" else "}"}"

    fun getType1(): String? = if (envelopingClass != null) envelopingClass.element?.refName else
        type ?: (oldParameter?.getReferable()?.let{ computeType(it) } ?: oldParameter?.getType1())

    fun getNameOrUnderscore(): String = getNameOrNull() ?: "_"

    fun getNameOrNull(): String? = name ?: oldParameter?.name

    fun isExternal() = externalScope != null

    fun isThis() = envelopingClass != null

    fun getThisDefClass(): ArendDefClass? = envelopingClass?.element

    fun getExternalScope() = externalScope?.element

    fun getReferable() = psiReferable?.element

    companion object {
        private fun <T : PsiElement> createSmartLink(obj : T): SmartPsiElementPointer<T> =
            SmartPointerManager.getInstance(obj.project).createSmartPsiElementPointer(obj)

        private fun computeType(psi: PsiElement): String? = when (val p = psi.parent) {
            is ArendNameTele -> p.type?.text
            is ArendTypeTele -> p.type?.text
            is ArendFieldTele -> p.type?.text
            is ArendPattern -> p.type?.text
            is ArendFieldDefIdentifier -> p.parentFieldTele?.type?.text
            is ArendClassStat, is ArendDefClass -> (psi as ArendClassField).resultType?.text
            is ArendTypedExpr -> p.type?.text
            is ArendIdentifierOrUnknown -> computeType(p)
            else -> throw IllegalArgumentException()
        }

        private fun computeExplicitness(referable: PsiElement): Boolean = when (val p = referable.parent) {
            is ArendNameTele -> p.isExplicit
            is ArendTypeTele -> p.isExplicit
            is ArendFieldTele -> p.isExplicit
            is ArendPattern -> p.isExplicit // TODO: Make sure that this is correct
            is ArendFieldDefIdentifier -> p.isExplicitField
            is ArendClassStat, is ArendDefClass -> true
            is ArendTypedExpr -> computeExplicitness(p.parent)
            is ArendIdentifierOrUnknown -> computeExplicitness(p.parent)
            else -> throw IllegalArgumentException()
        }

        fun createUnnamedDataParameter(type: String?) = ParameterDescriptor(false, type, null, true)

        fun createUnnamedParameter(tele: ArendTypeTele) = ParameterDescriptor(tele.isExplicit, tele.type?.text, null, false)

        fun createUnnamedParameter(tele: ArendNameTele) = ParameterDescriptor(tele.isExplicit, tele.type?.text, null, false)

        /**
         * Creates implicit parameter descriptor for a constructor
         * @param referable -- parameter psi (may be external)
         * @param data -- constructor's parent datatype (needed only to decide whether passed referable is external)
         */
        fun createNamedDataParameter(referable: Referable, data: ArendDefData) = ParameterDescriptor(
            false, computeType(referable as PsiElement), referable.refName, true,
            (referable as PsiElement).ancestor<ArendGroup>()
                ?.let { if (it != data) createSmartLink(it) else null }, // not null only for external parameters
            createSmartLink(referable)
        )

        fun createExternalParameter(referable: Referable, isExplicit: Boolean, type: ArendExpr?) =
            ParameterDescriptor(false, type?.text, referable.refName, false,
                (referable as PsiElement).ancestor<ArendGroup>()?.let { createSmartLink(it) }, createSmartLink(referable)
            )

        fun createFromTeles(params: List<Abstract.Parameter>): List<ParameterDescriptor> = params.map { tele -> when (tele) {
            is ArendTypeTele -> if (tele.typedExpr?.identifierOrUnknownList.isNullOrEmpty()) Collections.singletonList(
                createUnnamedParameter(tele)
            ) else
                tele.typedExpr?.identifierOrUnknownList!!.map { iou -> iou.defIdentifier?.let { ParameterDescriptor(it) } ?: createUnnamedParameter(tele)}
            is ArendNameTele -> if (tele.identifierOrUnknownList.isEmpty()) Collections.singletonList(
                createUnnamedParameter(tele)
            ) else
                tele.identifierOrUnknownList.map { iou -> iou.defIdentifier?.let { ParameterDescriptor(it, tele.isExplicit, tele.type?.text) } ?: createUnnamedParameter(tele)}
            is ArendFieldTele -> tele.referableList.map { ParameterDescriptor(tele.isExplicit, tele.type?.text, it.name) }
            else ->
                throw java.lang.IllegalArgumentException()
        }}.flatten()

        fun createThisParameter(defClass: ArendDefClass) = ParameterDescriptor(false, null, "this", false, null, null, createSmartLink(defClass))

        fun createDataParameter_(oldParameter: ParameterDescriptor?, externalScope: ArendGroup?, name: String?, type: String?) =
            ParameterDescriptor(false, type, name, true, externalScope?.let { createSmartLink(it) }, null, null, oldParameter)

        fun createThisParameter(oldParameter: ParameterDescriptor) =
            if (!oldParameter.isThis()) throw IllegalArgumentException() else
                ParameterDescriptor(false, null, "this", false, null, null, oldParameter.envelopingClass, oldParameter)

        fun identityTransform(list: List<ParameterDescriptor>): List<ParameterDescriptor> = list.map { pd ->
            ParameterDescriptor(pd.isExplicit, if (pd.getReferable() != null) null else pd.getType1(), pd.name, pd.isDataParameter,
                externalScope = pd.getExternalScope()?.let{ createSmartLink(it) },
                psiReferable = pd.getReferable()?.let{ createSmartLink(it) },
                envelopingClass = pd.getThisDefClass()?.let{ createSmartLink(it) },
                oldParameter = pd)
        }

        fun createNewParameter(isExplicit: Boolean, oldParameter: ParameterDescriptor?, externalScope: ArendGroup?, newName: String?, newType: String?) =
            ParameterDescriptor(isExplicit, newType, newName, false, externalScope?.let{ createSmartLink(it) }, null, null, oldParameter)
    }
}
/**
 * Stores context information that determines which definition parameters will be available to the user in a specific position
 *
 * Trivia: In Arend every definition may have multiple external parameters. Moreover, definitions written within the `dynamic part` of a class also depend on the implicit `this` parameter.
 * These parameters are only visible in certain contexts. For example {this} parameter is inaccessible from the `dynamic part` of the class.
 * Also parameters of a datatype constitute a part of constructor signature, but they are invisible in pattern expressions.
* */
class SignatureUsageContext private constructor(val isInsidePatternOrCoClause: Boolean,
                                                val envelopingDynamicClass: ArendDefClass?,
                                                val envelopingGroups: List<ArendGroup>) {
    fun filterParameters(list: List<ParameterDescriptor>): List<ParameterDescriptor> =
        list.filter {
            !(isInsidePatternOrCoClause && it.isDataParameter) &&
                   // !(it.isExternal() && envelopingGroups.contains(it.getExternalScope())) &&
                    !(it.isThis() && isInsidePatternOrCoClause)
        }

    companion object {
        fun getParameterContext(anchor: ArendCompositeElement): SignatureUsageContext {
            var isInsidePatternOrCoClause = false
            var envelopingDynamicClass: ArendDefClass? = null
            val envelopingGroups = ArrayList<ArendGroup>()
            var currentAnchor: PsiElement? = anchor
            while (currentAnchor != null) {
                if (currentAnchor.parent is CoClauseBase && currentAnchor.siblings(true, false).any{ it.elementType == FAT_ARROW })
                    isInsidePatternOrCoClause = true

                val parent = currentAnchor.parent

                if (currentAnchor is ArendPattern) isInsidePatternOrCoClause = true
                if (parent is ArendDefClass && currentAnchor !is ArendWhere) envelopingDynamicClass = parent
                if (currentAnchor is ArendDefFunction || currentAnchor is ArendDefData || currentAnchor is ArendDefModule) {
                    envelopingGroups.add(currentAnchor as ArendGroup)
                }

                currentAnchor = parent
            }

            return SignatureUsageContext(isInsidePatternOrCoClause, envelopingDynamicClass , envelopingGroups)
        }
    }
}