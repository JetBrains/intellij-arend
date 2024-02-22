package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.ArendLanguage
import org.arend.codeInsight.ArendCodeInsightUtils
import org.arend.codeInsight.ParameterDescriptor
import org.arend.naming.reference.ClassReferable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.search.ClassDescendantsSearch
import org.arend.term.abs.Abstract
import org.arend.term.abs.Abstract.ClassDefinition
import org.arend.term.abs.Abstract.ParametersHolder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

data class ArendChangeInfo (
    val parametersInfo: ArendParametersInfo,
    private val returnType: String?,
    val name: String,
    val locatedReferable: PsiLocatedReferable,
    private val nsCmds: List<NsCmdRefactoringAction> = emptyList()) : ChangeInfo {

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
    val refactoringDescriptors = ArrayList<ChangeSignatureRefactoringDescriptor>()

    init {
        val newParamsInDialog = newParameters.toList().map { it as ArendTextualParameter }
        val definition = method as ParametersHolder

        // TODO: Think whether we *really* need external parameters for the refactoring
        val externalParameters = ArendCodeInsightUtils.getExternalParameters(definition as PsiLocatedReferable) ?: throw IllegalStateException()
        val ownParameters = ParameterDescriptor.createFromTeles(definition.parameters)

        val newParameters = ArrayList<ParameterDescriptor>()
        newParameters.addAll(ParameterDescriptor.identityTransform(externalParameters))

        for (newParam in newParamsInDialog) {
            val oldIndex = newParam.oldIndex
            newParameters.add(ParameterDescriptor.createNewParameter(newParam.isExplicit(), ownParameters.getOrNull(oldIndex), ownParameters.getOrNull(oldIndex)?.getExternalScope(), newParam.name, newParam.typeText))
        }

        refactoringDescriptors.addAll(getRefactoringDescriptors(externalParameters + ownParameters, newParameters))
    }

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

    /**
     *  Calculates the list of refactoring descriptors associated with a ChangeSignatureRefactoring operation.
     *
     *  Trivia: In Arend one change signature refactoring operation may require changes in the signatures of multiple definitions (and/or their usages).
     *  For example if the signature of a class is changed, the signatures of its descendants need to be changed as well.
     *  Alternatively, if parameter of a definition is changed, some change may be required also for all sub-definitions which use this parameter as an external parameter
     *
     *  @param oldParameters  List of parameters of a definition upon which the refactoring is originally invoked.
     *                        The starting segment of the list should be the list of external parameters of the definition.
     *                        The ending segment of the list is the list of own parameters of the definition
     *                        (e.g. own parameters of a constructor in the case of a constructor, list of fieldDefIdentifiers in the case of a class, etc.)
     *  @param newParameters  The list of parameters of the definition after the refactoring operation.
     *                        The list should start with the list of (after refactoring) external parameters.
     *                        NewParameter.oldParameter references of elements in this list should refer to elements of the first list
     *  @return the set of descriptors specifying modifications of individual definitions.
     * */
    fun getRefactoringDescriptors(
        oldParameters: List<ParameterDescriptor>,
        newParameters: List<ParameterDescriptor>
    ): List<ChangeSignatureRefactoringDescriptor> {
        val refactoringDescriptors = ArrayList<ChangeSignatureRefactoringDescriptor>()
        when (val d = locatedReferable) {
            is ArendClassField -> {
                val thisParameter = ArendCodeInsightUtils.getThisParameter(d) ?: throw IllegalStateException()
                refactoringDescriptors.add(
                    ChangeSignatureRefactoringDescriptor(
                        d,
                        Collections.singletonList(thisParameter) + oldParameters,
                        Collections.singletonList(ParameterDescriptor.createThisParameter(thisParameter)) + newParameters,
                        newName = if (d.name != newName) newName else null
                    )
                )
            }

            is ArendConstructor -> {
                val thisParameter = ArendCodeInsightUtils.getThisParameterAsList(d)
                val thisNewParameter = thisParameter.firstOrNull()?.let {
                    Collections.singletonList(ParameterDescriptor.createThisParameter(it))
                } ?: emptyList()
                val data = d.ancestor<ArendDefData>() ?: throw java.lang.IllegalStateException()
                val dataParameters = (ArendCodeInsightUtils.getExternalParameters(data)
                    ?: emptyList()) + ParameterDescriptor.createFromTeles(data.parameters)
                val calculatedSignature = ArendCodeInsightUtils.getPartialExpectedConstructorSignature(d, dataParameters, ParameterDescriptor.identityTransform(dataParameters))

                refactoringDescriptors.add(
                    ChangeSignatureRefactoringDescriptor(d,
                        thisParameter + calculatedSignature.first + oldParameters,
                        thisNewParameter + calculatedSignature.second!! + newParameters,
                        newName = if (d.name != newName) newName else null
                    )
                )
            }

            is ArendDefClass -> {
                val descendants = ClassDescendantsSearch(d.project).getAllDescendants(d)
                val modifiedFieldDefIdentifiers = d.fieldTeleList.map { it.referableList }.flatten()
                for (clazz in descendants.filterIsInstance<ArendDefClass>().union(Collections.singletonList(d))) {
                    var modifiedArgumentStart = -1
                    var modifiedArgumentEnd = -1
                    val notImplementedFields = ClassReferable.Helper.getNotImplementedFields(clazz)
                    val clazzOldParameters = notImplementedFields.withIndex().map { (index, field) ->
                        val classParent = (field as PsiElement).ancestor<ArendDefClass>()!!
                        if (classParent == d) {
                            if (modifiedArgumentStart == -1) modifiedArgumentStart = index
                            modifiedArgumentEnd = index
                        }

                        ParameterDescriptor(field)
                    }
                    val prefix = clazzOldParameters.subList(0, modifiedArgumentStart)
                    val suffix = clazzOldParameters.subList(modifiedArgumentEnd + 1, clazzOldParameters.size)
                    val allClassFields = d.classStatList.mapNotNull { it.classField } + d.classFieldList
                    val centerPiece = this.newParameters.filter {
                        it.oldIndex == -1 || notImplementedFields.contains(modifiedFieldDefIdentifiers[it.oldIndex])
                    }.map {
                        if (it.oldIndex == -1) ParameterDescriptor.createNewParameter((it as ArendTextualParameter).isExplicit(), null, null, it.name, it.typeText) else {
                            val index = notImplementedFields.indexOf(modifiedFieldDefIdentifiers[it.oldIndex])
                            ParameterDescriptor.createNewParameter((it as ArendTextualParameter).isExplicit(), clazzOldParameters[index], null, it.name, it.typeText)
                        }
                    } + allClassFields.filter { notImplementedFields.contains(it) }.map {
                        val index = notImplementedFields.indexOf(it)
                        ParameterDescriptor.createNewParameter(true, clazzOldParameters[index], null, it.name, it.resultType?.text)
                    }
                    val clazzNewParameters = prefix.map {
                        ParameterDescriptor.createNewParameter(it.isExplicit, it, null, it.getNameOrUnderscore(), it.getType1())
                    } + centerPiece + suffix.map { ParameterDescriptor.createNewParameter(it.isExplicit, it, null, it.getNameOrUnderscore(), it.getType1()) }
                    refactoringDescriptors.add(
                        ChangeSignatureRefactoringDescriptor(clazz, clazzOldParameters, clazzNewParameters, newName = if (d == clazz) newName else null)
                    )
                }
            }

            is ArendDefData, is ArendFunctionDefinition<*> -> {
                val thisParameter = ArendCodeInsightUtils.getThisParameterAsList(d)
                val thisNewParameter =
                    thisParameter.firstOrNull()?.let { Collections.singletonList(ParameterDescriptor.createThisParameter(it)) }
                        ?: emptyList()
                val mainRefactoringDescriptor = ChangeSignatureRefactoringDescriptor(
                    d,
                    thisParameter + oldParameters,
                    thisNewParameter + newParameters,
                    newName = if (d.name != newName) newName else null
                )

                refactoringDescriptors.add(mainRefactoringDescriptor)
                val childDefinitions = getDefinitionsWithExternalParameters(d as ParametersHolder)
                for (childDef in childDefinitions) {
                    val childDefOldParameters =
                        ArendCodeInsightUtils.getParameterList(childDef as ParametersHolder).first!!
                    modifyExternalParameters(oldParameters, newParameters, childDef, childDefOldParameters)?.let {
                        refactoringDescriptors.add(it)
                    }
                }

                if (d is ArendDefData && !mainRefactoringDescriptor.isSetOrOrderPreserved()) for (cons in d.constructors) {
                    val constructorDataParameters =
                        ArendCodeInsightUtils.getPartialExpectedConstructorSignature(cons, oldParameters, newParameters)
                    val ownParameters = ParameterDescriptor.createFromTeles(cons.parameters)
                    val newOwnParameters = ParameterDescriptor.identityTransform(ownParameters)
                    refactoringDescriptors.add(
                        ChangeSignatureRefactoringDescriptor(
                            cons,
                            thisParameter + constructorDataParameters.first + ownParameters,
                            thisNewParameter + constructorDataParameters.second!! + newOwnParameters,
                        )
                    )
                }

            }

            else -> throw NotImplementedError()
        }
        return refactoringDescriptors
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
    fun addNamespaceCommands() {
        for (nsCmd in nsCmds) nsCmd.execute()
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
                /*is ArendClassField -> {
                    ph.ancestor<ArendDefClass>()?.let { result.add(it) }
                }
                is ArendDefClass -> {
                    result.add(ph)
                }*/
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