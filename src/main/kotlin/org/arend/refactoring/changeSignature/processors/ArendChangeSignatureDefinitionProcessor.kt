package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.psi.ArendElementTypes
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.*
import org.arend.term.abs.Abstract
import java.util.*
import kotlin.collections.ArrayList

abstract class ArendChangeSignatureDefinitionProcessor(protected val definition: Abstract.ParametersHolder, val info: ChangeInfo) {
  abstract fun getRefactoringDescriptors (implicitPrefix: List<Parameter>, mainParameters: List<Parameter>,
                                          newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>,
                                          isSetOrOrderPreserved: Boolean): Set<ChangeSignatureRefactoringDescriptor>

  open fun getSignatureEnd(): PsiElement? = null

  open fun fixEliminator() {}

  abstract fun getSignature(): String

  companion object {
      fun fixElim(elim: ArendElim?,
                  whitespaceBeforeBody: PsiElement,
                  clauses: List<Abstract.Clause>,
                  params: List<Abstract.Parameter>,
                  changeInfo: ArendChangeInfo
      ) {
          val withMode = elim == null || elim.withKw != null
          val allParameters = params.map { tele -> when (tele) {
              is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.map { iou -> iou.defIdentifier } ?: Collections.singletonList(
                  tele
              )
              is ArendNameTele -> tele.identifierOrUnknownList.map { iou -> iou.defIdentifier }
              else -> throw IllegalArgumentException()
          }}.flatten()

          val currentlyEliminatedParameters: List<Pair<Boolean /*explicitness*/, PsiElement?>> = if (elim == null || elim.withKw != null) {
              params.map { tele -> when (tele) {
                  is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.map { iou -> Pair(tele.isExplicit, iou.defIdentifier) } ?: Collections.singletonList(
                      Pair(tele.isExplicit, tele)
                  )
                  is ArendNameTele -> tele.identifierOrUnknownList.map { iou -> Pair(tele.isExplicit, iou.defIdentifier) }
                  else -> throw IllegalArgumentException()
              }}.flatten()
          } else elim.refIdentifierList.map { Pair(true, it.reference.resolve() as? ArendDefIdentifier) }.toList()

          val newEliminatedParameters: List<Pair<Boolean, Any?>> =
              if (withMode) changeInfo.newParameters.map { Pair((it as ArendParameterInfo).isExplicit(), if (it.oldIndex == -1) it.name else allParameters[it.oldIndex]) }.toList()
              else currentlyEliminatedParameters

          val preservedEliminatedParameters = changeInfo.newParameters.filter { it.oldIndex != -1 }.map { Pair((it as ArendParameterInfo).isExplicit(), allParameters[it.oldIndex]!!) }.filter { p -> currentlyEliminatedParameters.any{it.second == p.second} }
          val indicesOfDeletedParametersInCEP = currentlyEliminatedParameters.filter { p -> !preservedEliminatedParameters.any{ it.second == p.second} }.map { p -> currentlyEliminatedParameters.indexOfFirst{ p.second == it.second }}.sorted()

          val template = ArrayList<TemplateData>()
          if (withMode) {
              template.addAll(newEliminatedParameters.map { p -> TemplateData(if (p.second is PsiElement) allParameters.indexOf(p.second) else -1, p.first, false)})
              for (d in indicesOfDeletedParametersInCEP) template.add(TemplateData(d, isExplicit = true, isCommentedOut = true))
          } else {
              template.addAll(preservedEliminatedParameters.map { p -> TemplateData(currentlyEliminatedParameters.indexOfFirst { p.second == it.second }, isExplicit = true, isCommentedOut = false)})
              for (d in indicesOfDeletedParametersInCEP) if (d <= template.size) template.add(d, TemplateData(d, isExplicit = true, isCommentedOut = true))
          }

          val correctedElim =
              if (withMode) (if (newEliminatedParameters.isEmpty()) (if (elim == null) "" else "{-${elim.text}-}") else ArendElementTypes.WITH_KW.toString())
              else "${ArendElementTypes.ELIM_KW} ${printWithComments(currentlyEliminatedParameters, template, "") { (_, d), _ -> (d as? ArendDefIdentifier)?.name ?: "_" }}"

          for (clause in clauses) if (clause.patterns.isNotEmpty()) {
              val lastPatternOffset = (clause.patterns.last() as PsiElement).endOffset
              val arrowEndOffset = (clause as PsiElement).childrenWithLeaves.firstOrNull { (it as? PsiElement).elementType == ArendElementTypes.FAT_ARROW }?.endOffset ?: lastPatternOffset
              val suffix = clause.containingFile.text.substring(lastPatternOffset, arrowEndOffset)

              var j = 0
              val clausePatternsWithHoles = ArrayList<ArendPattern?>()

              for (param in currentlyEliminatedParameters) {
                  var pattern = clause.patterns.getOrNull(j)
                  if (pattern != null && pattern.isExplicit == param.first) {
                      j++
                  } else if (!param.first) {
                      pattern = null
                  }
                  clausePatternsWithHoles.add(pattern as? ArendPattern)
              }

              val newPatterns = printWithComments(clausePatternsWithHoles, template, suffix) { p, b ->
                  if (b == p.isExplicit) p.text
                  else if (!b && p.isExplicit) "{${p.text}}" else {
                      val lbrace = p.childrenWithLeaves.first { it.elementType == ArendElementTypes.LBRACE }
                      val rbrace = p.childrenWithLeaves.first { it.elementType == ArendElementTypes.RBRACE }
                      p.containingFile.text.substring(lbrace.endOffset, rbrace.startOffset)
                  }
              }

              performTextModification(clause, newPatterns, (clause.patterns.first() as PsiElement).startOffset, arrowEndOffset)
          }
          if (elim != null) performTextModification(elim, correctedElim) else {
              performTextModification(whitespaceBeforeBody, " $correctedElim", whitespaceBeforeBody.endOffset, whitespaceBeforeBody.endOffset)
          }
      }

      private data class TemplateData(val oldIndex: Int, val isExplicit: Boolean, val isCommentedOut: Boolean)
      private fun<T> printWithComments(list: List<T?>, t: List<TemplateData>, suffix: String, converter: (T, Boolean) -> String): String {
          val builder = StringBuilder(); var isInComment = false; var isAbsolutelyFirst = true; var isFirstUncommented = true
          val hasAtLeastOneUncommented = !t.all { it.isCommentedOut }
          for (tt in t) {
              if (tt.isCommentedOut && !isInComment) { builder.append("{-"); isInComment = true}
              if (!isAbsolutelyFirst && isFirstUncommented) builder.append(", ")
              if (isInComment && !tt.isCommentedOut) { builder.append("-}"); isInComment = false}
              if (!isAbsolutelyFirst && !isFirstUncommented) builder.append(", ")
              builder.append(list.getOrNull(tt.oldIndex)?.let{ converter.invoke(it, tt.isExplicit)} ?: "_")
              isAbsolutelyFirst = false
              if (!tt.isCommentedOut) isFirstUncommented = false
          }
          if (isInComment) {
              if (!hasAtLeastOneUncommented) builder.append(suffix)
              builder.append("-}")
          }
          if (hasAtLeastOneUncommented) builder.append(suffix)
          return builder.toString()
      }
  }
}