package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.term.abs.Abstract
import org.arend.term.abs.Abstract.ClassDefinition
import org.arend.term.abs.Abstract.ParametersHolder
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

data class ArendChangeInfo (
    val parametersInfo: ArendParametersInfo,
    private val returnType: String?,
    val name: String,
    val locatedReferable: PsiLocatedReferable,
    val deferredNsCmds: MutableList<NsCmdRefactoringAction> = ArrayList()) : ChangeInfo {

    private val pLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == PLEVELS_KW}
    private val pLevelParam = (locatedReferable as? ArendDefinition<*>)?.pLevelParameters
    private val pLevelsText = if (pLevelsKw != null && pLevelParam is PsiElement) " ${pLevelsKw.text} ${pLevelParam.text}" else ""
    private val hLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == HLEVELS_KW}
    private val hLevelParam = (locatedReferable as? ArendDefinition<*>)?.hLevelParameters
    private val hLevelsText = if (hLevelsKw != null && hLevelParam is PsiElement) " ${hLevelsKw.text} ${hLevelParam.text}" else ""
    private val precText = (locatedReferable as? ReferableBase<*>)?.prec?.let { "${it.text} " } ?: ""
    private val aliasText = (locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""
    private val extendsText = (locatedReferable as? ClassDefinition)?.let { class1 ->
        (class1 as? PsiElement)?.childrenWithLeaves?.filterIsInstance<ArendSuperClass>()?.lastOrNull()?.let { super1 ->
            class1.containingFile.text.substring((class1 as ArendDefClass).extendsKw!!.prevSibling.startOffset, super1.endOffset)
        }
    } ?: ""

    override fun getNewParameters(): Array<ParameterInfo> = parametersInfo.getNewParameters()

    override fun isParameterSetOrOrderChanged(): Boolean = parametersInfo.isParameterSetOrOrderChanged()

    override fun isParameterTypesChanged(): Boolean = parametersInfo.isParameterTypesChanged()

    override fun isParameterNamesChanged(): Boolean = parametersInfo.isParameterNamesChanged()

    fun getSignature(): String = when (val d = locatedReferable) {
        is ArendClassField ->
            "${(d.descendantOfType<ArendAccessMod>())?.let { it.text + " " } ?: ""}${precText}${name}${aliasText}${parametersInfo.parameterText()}${returnPart()}"
        is ArendConstructor ->
            "${precText}${name}${aliasText}${parametersInfo.parameterText()}${returnPart()}"
        is ArendDefClass ->
            "${if (d.isRecord) RECORD_KW else CLASS_KW} ${precText}${name}${pLevelsText}${hLevelsText}${aliasText}${parametersInfo.parameterText()}${extendsText}"
        is ArendDefData ->
            "${d.truncatedKw?.text?.let { "$it " } ?: ""}${DATA_KW} ${precText}${name}${pLevelsText}${hLevelsText}${aliasText}${parametersInfo.parameterText()}${returnPart()}"
        is ArendDefFunction ->
            "${d.functionKw.text} ${precText}${name}${pLevelsText}${hLevelsText}${aliasText}${parametersInfo.parameterText()}${returnPart()}"
        is ArendDefInstance ->
            "$INSTANCE_KW ${precText}${name}${pLevelsText}${hLevelsText}${aliasText}${parametersInfo.parameterText()}${returnPart()}"
        else -> throw NotImplementedError()
    }

    fun getSignatureEndPositionPsi(): PsiElement? = when (val d = locatedReferable) {
        is ArendConstructor -> d.elim
        is ArendDefClass -> d.lbrace ?: d.childrenWithLeaves.filter { it.elementType == PIPE }.firstOrNull() ?: d.where
        is ArendDefData -> d.dataBody ?: d.where
        is ArendFunctionDefinition<*> -> d.body ?: d.where
        else -> null
    }

    fun modifySignature() {
        if (isParameterNamesChanged || isParameterSetOrOrderChanged || isParameterTypesChanged || isReturnTypeChanged) {
            val signatureEndPsi: PsiElement? = getSignatureEndPositionPsi()
            val signature = getSignature()
            performTextModification(method, signature, method.startOffset, signatureEndPsi?.findPrevSibling()?.endOffset ?: method.endOffset)
        } else if (isNameChanged)
            (method as ArendDefinition<*>).setName(newName)
    }

    override fun isGenerateDelegate(): Boolean = false

    override fun getMethod(): PsiElement = locatedReferable

    override fun isReturnTypeChanged(): Boolean {
        val returnExpr = getReturnExpr(locatedReferable) ?: return !returnType.isNullOrEmpty()
        return returnExpr.text != (returnType ?: "")
    }

    override fun isNameChanged(): Boolean = newName != locatedReferable.defIdentifier?.name

    override fun getNewName(): String = name

    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    fun returnPart(): String {
        val returnExpr = getReturnExpr(locatedReferable)
        var colonWhitespace = ""
        var pointer: PsiElement? = returnExpr?.prevSibling
        while (pointer != null && !locatedReferable.children.contains(pointer)) {
            colonWhitespace = pointer.text + colonWhitespace
            pointer = pointer.prevSibling
        }
        return when {
            returnType == null && returnExpr == null -> ""
            returnType == null && returnExpr != null -> "$colonWhitespace${returnExpr.text}"
            returnType != null && returnType.isEmpty() -> ""
            returnType != null && returnExpr == null -> " : $returnType"
            else -> "$colonWhitespace$returnType"
        }
    }

    companion object {

        fun getDefinitionsWithExternalParameters(ph: ParametersHolder): Set<PsiLocatedReferable> {
            val result = HashSet<PsiLocatedReferable>()
            when (ph) {
                is ArendDefFunction, is ArendDefInstance, is ArendDefData -> {
                    result.add(ph as PsiLocatedReferable)
                    (ph as PsiElement).childOfType<ArendWhere>()?.descendantsOfType<PsiLocatedReferable>()?.filterNot { it is ArendConstructor || it is ArendClassField }?.let { result.addAll(it) }
                }
                is ArendConstructor -> {
                    ph.ancestor<ArendDefData>()?.let { result.add(it) }
                }
            }

            return result
        }

        fun getTeles(locatedReferable: PsiLocatedReferable): List<Abstract.Parameter> = when (locatedReferable) {
            is ArendFunctionDefinition<*> -> locatedReferable.parameters
            is ArendDefData -> locatedReferable.parameters
            is ArendDefClass -> locatedReferable.fieldTeleList
            is ArendDefMeta -> locatedReferable.parameters
            is ArendConstructor -> locatedReferable.parameters
            is ArendClassField -> locatedReferable.parameters
            else ->
                throw NotImplementedError()
        }

        fun getReturnExpr(locatedReferable: PsiLocatedReferable): PsiElement? {
            val returnET = when (locatedReferable) {
                is ArendFunctionDefinition<*>, is ArendClassField -> RETURN_EXPR
                is ArendDefData -> UNIVERSE_EXPR
                is ArendConstructor -> NEW_EXPR
                else -> null
            }
            return locatedReferable.children.firstOrNull { it.elementType == returnET }
        }
    }
}