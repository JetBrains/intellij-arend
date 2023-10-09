package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.refactoring.changeSignature.processors.*
import org.arend.term.abs.Abstract.ClassDefinition
import org.arend.term.abs.Abstract.ParametersHolder
import java.util.Collections.singletonList
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

data class ArendChangeInfo (
    private val parameterInfo : List<ArendParameterInfo>,
    private val returnType: String?,
    val name: String,
    val locatedReferable: PsiLocatedReferable,
    private val nsCmds: List<NsCmdRefactoringAction> = emptyList()) : ChangeInfo {
    private data class TeleWhitespaceData (val beforeTeleWhitespace: String, val colonText: String?, val afterTypeText: String?)
    private val parameterToTeleWhitespaceData = HashMap<Int, TeleWhitespaceData>()
    private val parameterWhitespaceData = HashMap<Int, String>()

    private val pLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == PLEVELS_KW}
    private val pLevelParam = (locatedReferable as? ArendDefinition<*>)?.pLevelParameters
    val pLevelsText = if (pLevelsKw != null && pLevelParam is PsiElement) " ${pLevelsKw.text} ${pLevelParam.text}" else ""
    private val hLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == HLEVELS_KW}
    private val hLevelParam = (locatedReferable as? ArendDefinition<*>)?.hLevelParameters
    val hLevelsText = if (hLevelsKw != null && hLevelParam is PsiElement) " ${hLevelsKw.text} ${hLevelParam.text}" else ""
    val precText = (locatedReferable as? ReferableBase<*>)?.prec?.let { "${it.text} " } ?: ""
    val aliasText = (locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""
    val extendsText = (locatedReferable as? ClassDefinition)?.let {class1 ->
        (class1 as? PsiElement)?.childrenWithLeaves?.filterIsInstance<ArendSuperClass>()?.lastOrNull()?.let { super1 ->
            class1.containingFile.text.substring((class1 as ArendDefClass).extendsKw!!.prevSibling.startOffset, super1.endOffset)
        }
    } ?: ""
    val changeSignatureProcessor = getChangeInfoProcessor(method as ParametersHolder, this)

    init {
        var count = 0
        if (locatedReferable is ParametersHolder) {
            for (tele in locatedReferable.parameters) when (tele) {
                is ArendNameTele -> {
                    val beforeTeleWhitespace = tele.getWhitespace(SpaceDirection.LeadingSpace) ?: continue
                    val colonText = tele.type?.let { it.getWhitespace(SpaceDirection.LeadingSpace, true) }
                    val afterTypeText = tele.type?.let{ it.getWhitespace(SpaceDirection.TrailingSpace) }
                    val data = TeleWhitespaceData(beforeTeleWhitespace, colonText, afterTypeText)
                    for (id in tele.identifierOrUnknownList) {
                        parameterToTeleWhitespaceData[count] = data
                        id.getWhitespace(SpaceDirection.LeadingSpace)?.let{ parameterWhitespaceData[count] = it}
                        count++
                    }
                }
            }
        }
    }

    override fun getNewParameters(): Array<ParameterInfo> = parameterInfo.toTypedArray()

    override fun isParameterSetOrOrderChanged(): Boolean { //Or explicitness of arguments
        val oldParameterInfo = getParameterInfo(locatedReferable)
        if (oldParameterInfo.size != parameterInfo.size) return true
        for ((i, p) in parameterInfo.withIndex()) if (p.oldIndex != i || p.isExplicit() != oldParameterInfo[i].isExplicit()) return true
        return false
    }

    override fun isParameterTypesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo) if (p.oldIndex != -1 && p.typeText != oldParameterInfo[p.oldIndex].typeText) return true
        return false
    }

    override fun isParameterNamesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo) if (p.oldIndex != -1 && p.name != oldParameterInfo[p.oldIndex].name) return true
        return false
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

    fun parameterText(): String {

        data class TeleEntry(val typeText: String?, val isExplicit: Boolean, val isCoerce: Boolean, val isClassifying: Boolean, val isProperty: Boolean, val accessModifier: org.arend.term.group.AccessModifier = org.arend.term.group.AccessModifier.PUBLIC, val parameterNames: MutableList<Pair<String?, Int>>)
        val teleEntries = ArrayList<TeleEntry>()
        val associatedWhitespaceData = HashSet<Pair<TeleEntry, TeleWhitespaceData>>()
        val usedWhitespaceData = HashSet<TeleWhitespaceData>()

        for (parameter in parameterInfo) {
            if (teleEntries.isEmpty() || teleEntries.last().typeText != parameter.typeText || teleEntries.last().isExplicit != parameter.isExplicit() || parameter.isCoerce || parameter.isClassifying)
                teleEntries.add(TeleEntry(parameter.typeText, parameter.isExplicit(), parameter.isCoerce, parameter.isClassifying, parameter.isProperty, parameter.accessModifier, singletonList(Pair(parameter.name, parameter.oldIndex)).toMutableList())) else
                    teleEntries.last().parameterNames.add(Pair(parameter.name, parameter.oldIndex))
            val data = parameterToTeleWhitespaceData[parameter.oldIndex]
            if (data != null && !usedWhitespaceData.contains(data)) {
                usedWhitespaceData.add(data)
                associatedWhitespaceData.add(Pair(teleEntries.last(), data))
            }
        }

        val newTeles = StringBuilder()
        for (entry in teleEntries) {
            val whitespaceData = associatedWhitespaceData.firstOrNull { it.first == entry }?.second
            newTeles.append(whitespaceData?.beforeTeleWhitespace ?: " ")
            val parameterNames = StringBuilder()
            for ((i, p) in entry.parameterNames.withIndex()) {
                var space = parameterWhitespaceData[p.second] ?: " "
                if (i == 0 && space.startsWith(" ")) space = space.drop(1)
                if (i > 0 && space.isEmpty()) space = " "
                parameterNames.append(space)
                parameterNames.append(p.first.let{ if (it.isNullOrEmpty()) UNDERSCORE else it })
            }

            val referableAllowsTypeTeles = locatedReferable is ArendConstructor || locatedReferable is ArendDefData
            val printParameterNames = parameterNames.toString() != "_" || !referableAllowsTypeTeles
            val printSurroundingParens = !entry.isExplicit || entry.typeText == null || printParameterNames
            if (printSurroundingParens) newTeles.append (if (entry.isExplicit) LPAREN else LBRACE)
            when (entry.accessModifier) {
                org.arend.term.group.AccessModifier.PRIVATE -> newTeles.append("$PRIVATE_KW ")
                org.arend.term.group.AccessModifier.PROTECTED -> newTeles.append("$PROTECTED_KW ")
                else -> {}
            }
            if (entry.isCoerce) newTeles.append("$COERCE_KW ") else
                if (entry.isClassifying) newTeles.append("$CLASSIFYING_KW ") else
                    if (entry.isProperty) newTeles.append("$PROPERTY_KW ")


            if (entry.typeText != null) {
                if (printParameterNames) {
                    newTeles.append(parameterNames.toString())
                    newTeles.append(whitespaceData?.colonText ?: " $COLON ")
                }
                newTeles.append(if (entry.typeText.trim().contains(" ") && !printParameterNames && !printSurroundingParens) "(${entry.typeText})" else entry.typeText)
            } else {
                newTeles.append(parameterNames.toString())
            }

            newTeles.append(whitespaceData?.afterTypeText ?: "")
            if (printSurroundingParens) newTeles.append (if (entry.isExplicit) RPAREN else RBRACE)
        }
        return newTeles.toString()
    }
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
    fun addNamespaceCommands() {
        for (nsCmd in nsCmds) nsCmd.execute()
    }

    companion object {
        fun getChangeInfoProcessorClass(ph: ParametersHolder) : KClass<out ArendChangeSignatureDefinitionProcessor>? = when (ph) {
            is ArendDefFunction, is ArendDefInstance -> ArendDefFunctionChangeSignatureProcessor::class
            is ArendDefData -> ArendDefDataChangeSignatureProcessor::class
            is ArendConstructor -> ArendConstructorChangeSignatureProcessor::class
            is ArendClassField -> ArendClassFieldChangeSignatureProcessor::class
            is ArendDefClass -> ArendDefClassChangeSignatureProcessor::class
            else -> null
        }
        fun getChangeInfoProcessor(ph: ParametersHolder, info: ArendChangeInfo?) : ArendChangeSignatureDefinitionProcessor =
            getChangeInfoProcessorClass(ph)!!.primaryConstructor!!.call(ph, info)

        fun getParameterInfo(locatedReferable: PsiLocatedReferable): MutableList<ArendParameterInfo> {
            var index = 0
            val result = ArrayList<ArendParameterInfo>()

            for (t in getTeles(locatedReferable)) when (t) {
                is ArendNameTele -> for (parameter in t.identifierOrUnknownList) {
                    result.add(ArendParameterInfo(parameter.defIdentifier?.name ?: "_", t.type?.oneLineText, index, t.isExplicit, isProperty = t.isProperty, correspondingReferable = parameter.defIdentifier))
                    index++
                }
                is ArendTypeTele -> {
                    val type = t.type?.text
                    var i = 0
                    t.typedExpr?.identifierOrUnknownList?.forEach { iOU ->
                        i++
                        iOU.defIdentifier?.let { dI -> result.add(ArendParameterInfo(dI.name, type, index, t.isExplicit, isProperty = t.isProperty, correspondingReferable = iOU.defIdentifier)) }
                        index++
                    }
                    if (i == 0) {
                        result.add(ArendParameterInfo("_", type, index, t.isExplicit, isProperty = t.isProperty, correspondingReferable = t))
                        index++
                    }
                }
                is ArendNameTeleUntyped -> {
                    result.add(ArendParameterInfo(t.defIdentifier.name, null, index, t.isExplicit, isProperty = t.isProperty, correspondingReferable = t))
                    index++
                }

                is ArendFieldTele -> {
                    for (fdI in t.referableList) {
                        result.add(ArendParameterInfo(fdI.name, t.type?.text ?: "", index, t.isExplicit, t.isClassifying, t.isCoerce, t.isProperty, t.descendantOfType<ArendAccessMod>()?.accessModifier ?: org.arend.term.group.AccessModifier.PUBLIC, correspondingReferable = fdI))
                        index++
                    }
                }
            }
            return result
        }
        fun getTeles(locatedReferable: PsiLocatedReferable): List<PsiElement> = when (locatedReferable) {
            is ArendFunctionDefinition<*> -> locatedReferable.parameters
            is ArendDefData -> locatedReferable.parameters
            is ArendDefClass -> locatedReferable.fieldTeleList
            is ArendDefMeta -> locatedReferable.parameters
            is ArendConstructor -> locatedReferable.parameters
            is ArendClassField -> locatedReferable.parameters
            else -> throw NotImplementedError()
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