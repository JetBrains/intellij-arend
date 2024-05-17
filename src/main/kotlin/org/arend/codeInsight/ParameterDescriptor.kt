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

enum class ClassParameterKind {CLASS_FIELD, INHERITED_PARAMETER, OWN_PARAMETER}
class ParameterDescriptor private constructor(
    val name: String?,
    val isExplicit: Boolean,
    val typeGetter: () -> String?,
    val isDataParameter: Boolean,
    private val externalScopeLink: SmartPsiElementPointer<ArendGroup>? = null,
    private val referableLink: SmartPsiElementPointer<PsiElement>? = null,
    private val envelopingClassLink: SmartPsiElementPointer<ArendDefClass>? = null,
    val oldParameter: ParameterDescriptor? = null,
    val classParameterKind: ClassParameterKind? = null
) {
    init {
        if (externalScopeLink != null) when (externalScopeLink.element) {
            is ArendDefFunction, is ArendDefData, is ArendDefInstance/*, is ArendDefModule */-> {}
            else -> throw IllegalArgumentException()
        }
    }

    constructor(
        name: String?,
        isExplicit: Boolean,
        type: String?,
        classParameterKind: ClassParameterKind?,
        externalScope: ArendGroup? = null
    ):
            this(
                name,
                isExplicit,
                { type },
                false,
                externalScopeLink = createSmartLink(externalScope),
                classParameterKind = classParameterKind
            )

    override fun toString(): String = "${if (isExplicit) "(" else "{"}${getNameOrUnderscore()}${if (getType() != null) " : " + getType() else ""}${if (isExplicit) ")" else "}"}"

    fun getType(): String? = typeGetter.invoke()

    fun getNameOrUnderscore(): String = getNameOrNull() ?: "_"

    fun getNameOrNull(): String? = name ?: oldParameter?.name

    fun isExternal() = externalScopeLink != null

    fun isThis() = envelopingClassLink != null

    fun getThisDefClass(): ArendDefClass? = envelopingClassLink?.element

    fun getExternalScope() = externalScopeLink?.element

    fun getReferable() = referableLink?.element

    companion object {
        open class Factory {
            open fun<T : PsiElement> typeGetter(psi: T, getter: (T) -> String?): () -> String? {
                val smartLink = createSmartLink(psi)!!
                return {
                    val psiNew = smartLink.element
                    if (psiNew != null) getter.invoke(psiNew) else null
                }
            }

            open fun createUnnamedParameter(tele: ArendTypeTele) =
                ParameterDescriptor(null, tele.isExplicit, { tele.type?.text }, false)

            open fun createFromReferable(referable: Referable,
                                         isExplicit: Boolean = computeExplicitness(referable as PsiElement),
                                         externalScope: ArendGroup? = null,
                                         classParameterKind: ClassParameterKind? = null) =
                ParameterDescriptor(
                    name = referable.refName,
                    isExplicit = isExplicit,
                    typeGetter = typeGetter(referable as PsiElement) { psi -> computeType(psi) },
                    isDataParameter = false,
                    externalScopeLink = createSmartLink(externalScope),
                    referableLink = createSmartLink(referable),
                    classParameterKind = classParameterKind
                )

            open fun createUnnamedDataParameter(type: String?) = ParameterDescriptor(null, false, { type }, true)

            open fun createUnnamedParameter(tele: ArendNameTele) = ParameterDescriptor(
                null,
                tele.isExplicit,
                { tele.type?.text },
                false
            )

            /**
             * Creates implicit parameter descriptor for a constructor
             * @param referable -- parameter psi (may be external)
             * @param data -- constructor's parent datatype (needed only to decide whether passed referable is external)
             */
            open fun createNamedDataParameter(referable: Referable, data: ArendDefData) = ParameterDescriptor(
                referable.refName, false, { computeType(referable as PsiElement) }, true,
                (referable as PsiElement).ancestor<ArendGroup>()
                    ?.let { if (it != data) createSmartLink(it) else null }, // not null only for external parameters
                createSmartLink(referable)
            )

            open fun createExternalParameter(referable: Referable,
                                             typeGetter: () -> String? = typeGetter(referable as PsiReferable) { computeType(it) },
                                             parameterKind: ClassParameterKind? = null) =
                ParameterDescriptor(
                    name = referable.refName,
                    isExplicit = false,
                    typeGetter = typeGetter,
                    isDataParameter = false,
                    externalScopeLink = createSmartLink((referable as PsiElement).ancestor<ArendGroup>()),
                    referableLink = createSmartLink(referable),
                    classParameterKind = parameterKind
                )

            open fun createThisParameter(defClass: ArendDefClass) =
                ParameterDescriptor(
                    name = "this",
                    isExplicit = false,
                    typeGetter = typeGetter(defClass) { clazz -> clazz.refName },
                    isDataParameter = false,
                    externalScopeLink = null,
                    referableLink = null,
                    envelopingClassLink = createSmartLink(defClass)
                )

            open fun createDataParameter(oldParameter: ParameterDescriptor?, externalScope: ArendGroup?, name: String?, typeGetter: () -> String?, referable: PsiReferable?) =
                ParameterDescriptor(
                    name = name,
                    isExplicit = false,
                    typeGetter = typeGetter,
                    isDataParameter = true,
                    externalScopeLink = createSmartLink(externalScope),
                    referableLink = createSmartLink(referable),
                    envelopingClassLink = null,
                    oldParameter = oldParameter
                )

            open fun createThisParameter(oldParameter: ParameterDescriptor) =
                if (!oldParameter.isThis()) throw IllegalArgumentException() else
                    ParameterDescriptor(
                        name = "this",
                        isExplicit = false,
                        typeGetter = { oldParameter.envelopingClassLink?.element?.refName },
                        isDataParameter = false,
                        externalScopeLink = null,
                        referableLink = null,
                        envelopingClassLink = oldParameter.envelopingClassLink,
                        oldParameter = oldParameter
                    )

            open fun identityTransform(list: List<ParameterDescriptor>): List<ParameterDescriptor> = list.map { pd ->
                ParameterDescriptor(
                    name = pd.name,
                    isExplicit = pd.isExplicit,
                    typeGetter = pd.typeGetter,
                    isDataParameter = pd.isDataParameter,
                    externalScopeLink = pd.externalScopeLink,
                    referableLink = pd.referableLink,
                    envelopingClassLink = pd.envelopingClassLink,
                    oldParameter = pd,
                    classParameterKind = pd.classParameterKind
                )
            }

            open fun createNewParameter(isExplicit: Boolean, oldParameter: ParameterDescriptor?, externalScope: ArendGroup?, newName: String?, newType: () -> String?, classParameterKind: ClassParameterKind? = null) =
                ParameterDescriptor(
                    newName,
                    isExplicit,
                    newType,
                    false,
                    createSmartLink(externalScope),
                    null,
                    null,
                    oldParameter,
                    classParameterKind
                )

            fun createFromTeles(params: List<Abstract.Parameter>): List<ParameterDescriptor> = params.map { tele -> when (tele) {
                is ArendTypeTele -> if (tele.typedExpr?.identifierOrUnknownList.isNullOrEmpty()) Collections.singletonList(
                    createUnnamedParameter(tele)
                ) else
                    tele.typedExpr?.identifierOrUnknownList!!.map { iou -> iou.defIdentifier?.let { createFromReferable(it) } ?: createUnnamedParameter(tele) }
                is ArendNameTele -> if (tele.identifierOrUnknownList.isEmpty()) Collections.singletonList(
                    createUnnamedParameter(tele)
                ) else
                    tele.identifierOrUnknownList.map { iou -> iou.defIdentifier?.let { createFromReferable(it, tele.isExplicit) } ?: createUnnamedParameter(tele) }
                is ArendFieldTele -> tele.referableList.map { createFromReferable(it, tele.isExplicit) }
                else ->
                    throw java.lang.IllegalArgumentException()
            }}.flatten()
        }
    }
}

private fun computeExplicitness(referable: PsiElement): Boolean = when (val p = referable.parent) {
    is ArendNameTele -> p.isExplicit
    is ArendTypeTele -> p.isExplicit
    is ArendFieldTele -> p.isExplicit
    is ArendPattern -> p.isExplicit
    is ArendFieldDefIdentifier -> p.isExplicitField
    is ArendClassStat, is ArendDefClass -> true
    is ArendTypedExpr -> computeExplicitness(p.parent)
    is ArendIdentifierOrUnknown -> computeExplicitness(p.parent)
    else -> throw IllegalArgumentException()
}

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

object DefaultParameterDescriptorFactory: ParameterDescriptor.Companion.Factory()

/**
 * Stores context information that determines which definition parameters will be available to the user in a specific position
 *
 * Trivia: In Arend every definition may have multiple external parameters. Moreover, definitions written within the `dynamic part` of a class also depend on the implicit `this` parameter.
 * These parameters are only visible in certain contexts. For example {this} parameter is inaccessible from the `dynamic part` of the class.
 * Also parameters of a datatype constitute a part of constructor signature, but they are invisible in pattern expressions.
* */
class SignatureUsageContext private constructor(
    private val isInsidePatternOrCoClause: Boolean,
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
            var isInsidePatternOrCoClause = anchor is CoClauseBase
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

fun <T : PsiElement> createSmartLink(obj : T?): SmartPsiElementPointer<T>? =
    obj?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it) }