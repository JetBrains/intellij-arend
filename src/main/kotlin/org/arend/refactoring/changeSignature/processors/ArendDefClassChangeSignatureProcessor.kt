package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.naming.reference.ClassReferable
import org.arend.psi.ArendElementTypes
import org.arend.psi.ancestor
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.ArendClassField
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendFieldDefIdentifier
import org.arend.refactoring.changeSignature.*
import org.arend.search.ClassDescendantsSearch
import java.util.*

class ArendDefClassChangeSignatureProcessor(val defClass: ArendDefClass, val changeInfo: ArendChangeInfo):
        ArendChangeSignatureDefinitionProcessor(defClass, changeInfo) {
    override fun getRefactoringDescriptors(implicitPrefix: List<Parameter>, mainParameters: List<Parameter>, newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>, isSetOrOrderPreserved: Boolean): Set<ChangeSignatureRefactoringDescriptor> {
        val refactoringDescriptors = HashSet<ChangeSignatureRefactoringDescriptor>()

        val descendants = ClassDescendantsSearch(defClass.project).getAllDescendants(defClass)
        val modifiedFieldDefIdentifiers = defClass.fieldTeleList.map { it.referableList }.flatten()
        for (clazz in descendants.filterIsInstance<ArendDefClass>().union(Collections.singletonList(defClass))) {
            var modifiedArgumentStart = -1
            var modifiedArgumentEnd = -1
            val notImplementedFields = ClassReferable.Helper.getNotImplementedFields(clazz)
            val clazzOldParameters = notImplementedFields.withIndex().map {
                val isExplicit = when (it.value) {
                    is ArendFieldDefIdentifier -> it.value.isExplicitField
                    is ArendClassField -> true
                    else -> throw IllegalStateException()
                }
                val classParent = (it.value as PsiElement).ancestor<ArendDefClass>()!!
                if (classParent == defClass) {
                    if (modifiedArgumentStart == -1) modifiedArgumentStart = it.index
                    modifiedArgumentEnd = it.index
                }

                Parameter(isExplicit, it.value)
            }
            val prefix = clazzOldParameters.subList(0, modifiedArgumentStart)
            val suffix = clazzOldParameters.subList(modifiedArgumentEnd+1, clazzOldParameters.size)
            val allClassFields = defClass.classStatList.mapNotNull { it.classField } + defClass.classFieldList
            val centerPiece = info.newParameters.filter { it.oldIndex == -1 || notImplementedFields.contains(modifiedFieldDefIdentifiers[it.oldIndex]) }.map {
                if (it.oldIndex == -1) NewParameter((it as ArendParameterInfo).isExplicit(), null) else {
                    val index = notImplementedFields.indexOf(modifiedFieldDefIdentifiers[it.oldIndex])
                    NewParameter((it as ArendParameterInfo).isExplicit(), clazzOldParameters[index])
                }
            } + allClassFields.filter{ notImplementedFields.contains(it) }.map {
                val index = notImplementedFields.indexOf(it)
                NewParameter(true, clazzOldParameters[index])
            }
            val clazzNewParameters = prefix.map {
                NewParameter(
                    it.isExplicit,
                    it
                )
            } + centerPiece + suffix.map { NewParameter(it.isExplicit, it) }
            refactoringDescriptors.add(
                ChangeSignatureRefactoringDescriptor(
                    clazz,
                    clazzOldParameters,
                    clazzNewParameters,
                    newName = if (defClass == clazz) info.newName else null
                )
            )
        }
        return refactoringDescriptors
    }

    override fun getSignatureEnd(): PsiElement? = defClass.lbrace ?: defClass.childrenWithLeaves.filter { it.elementType == ArendElementTypes.PIPE }.firstOrNull() ?: defClass.where
    override fun getSignature(): String = "${ArendElementTypes.CLASS_KW} ${changeInfo.precText}${changeInfo.name}${changeInfo.pLevelsText}${changeInfo.hLevelsText}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.extendsText}"
}