package org.arend.quickfix.implementCoClause

import com.google.common.collect.Sets.combinations
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.settings.ArendProjectStatistics
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle
import kotlin.math.min

open class ImplementFieldsQuickFix(private val instanceRef: SmartPsiElementPointer<PsiElement>,
                              private val needsBulb: Boolean,
                              private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>): IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun getText() = ArendBundle.message("arend.coClause.implementMissing")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        instanceRef.element != null

    /* TODO[server2]
    private fun collectDefaultStatements(defClass: ArendDefClass, defaultFields: MutableSet<ArendClassStat>) {
        defaultFields.addAll(defClass.classStatList.filter { it.isDefault })
        for (superClass in defClass.superClassReferences) {
            (superClass as? ArendDefClass?)?.let { collectDefaultStatements(it, defaultFields) }
        }
    }
    */

    private fun getMinGroup(defaultFields: Set<PsiElement>, rules: Map<PsiElement, List<PsiElement>>): Set<Set<PsiElement>> {
        val results = mutableSetOf<Set<PsiElement>>()
        for (groupSize in 1..min(DEFAULT_FIELDS_LIMIT, defaultFields.size)) {
            if (results.isNotEmpty()) {
                break
            }
            val groups = combinations(defaultFields.toSet(), groupSize)
            for (group in groups) {
                val derivedFields = group.toMutableSet()
                while (derivedFields.size < defaultFields.size) {
                    var added = false
                    for ((field, arguments) in rules) {
                        if (!derivedFields.contains(field) && arguments.all { derivedFields.contains(it) }) {
                            derivedFields.add(field)
                            added = true
                        }
                    }
                    if (!added) {
                        break
                    }
                }
                if (derivedFields.size == defaultFields.size) {
                    results.add(group)
                }
            }
        }
        return results
    }

    private fun getMinDefaultFields(): Pair<Set<Set<PsiElement>>, Set<PsiElement>> {
        val defaultStatements = mutableSetOf<ArendClassStat>()
        /*
        ((instanceRef.element as Abstract.ClassReferenceHolder).classReference as? ArendDefClass?)?.let {
            collectDefaultStatements(it, defaultStatements)
        }
        */
        val defaultFields = defaultStatements.mapNotNull { it.coClause?.longName?.resolve }.toSet()

        val rules = mutableMapOf<PsiElement, MutableList<PsiElement>>()
        for (defaultStatement in defaultStatements) {
            val defaultField = defaultStatement.coClause?.longName?.resolve ?: continue
            val arguments = (defaultStatement.coClause?.expr as? ArendNewExpr?)?.argumentAppExpr?.argumentList
                ?.mapNotNull { (it as? ArendAtomArgument?)?.atomFieldsAcc }?.toMutableList() ?: mutableListOf()
            (defaultStatement.coClause?.expr as? ArendNewExpr?)?.argumentAppExpr?.atomFieldsAcc?.let { arguments.add(it) }
            for (argument in arguments) {
                val defaultArgument = argument.atom.literal?.refIdentifier?.resolve ?: continue
                if (!defaultFields.contains(defaultArgument)) {
                    continue
                }
                rules.getOrPut(defaultField) { mutableListOf() }.add(defaultArgument)
            }
        }

        val defaultDependentFields = defaultFields.filter { rules[it] != null }.toSet()
        val result = if (rules.isEmpty()) {
            emptySet()
        } else {
            getMinGroup(defaultDependentFields, rules)
        }
        return Pair(result, defaultFields)
    }

    private fun addField(field: Referable, inserter: AbstractCoClauseInserter, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = inserter.coClausesList
        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            inserter.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = when (anchor) {
                is ArendCoClause -> psiFactory.createCoClause(name)
                is ArendLocalCoClause -> psiFactory.createLocalCoClause(name)
                else -> null
            }

            if (coClause != null) {
                val pipeSample = coClause.findPrevSibling()
                val whitespace = psiFactory.createWhitespace(" ")
                val insertedCoClause = anchor.parent.addAfter(coClause, anchor)
                if (insertedCoClause is ArendCoClause && pipeSample != null) {
                    anchor.parent.addBefore(pipeSample, insertedCoClause)
                    anchor.parent.addBefore(whitespace, insertedCoClause)
                }
                if (!caretMoved && editor != null) {
                    moveCaretToEndOffset(editor, anchor.nextSibling)
                    caretMoved = true
                }
                anchor.parent.addAfter(psiFactory.createWhitespace("\n  "), anchor)
            }
        }
    }

    private fun getFullClassName(): String {
        return ""
        /* TODO[server2]
        val classReferable = (instanceRef.element as ArendDefInstance).classReference as PsiLocatedReferable
        val name = (classReferable.containingFile as ArendFile).moduleLocation.toString() + "." + classReferable.fullName
        return name
        */
    }

    private fun showFields(
        project: Project,
        editor: Editor,
        variants: MutableList<MutableList<LocatedReferable>>,
        allFields: Map<LocatedReferable, Boolean>,
        baseFields: List<Pair<LocatedReferable, Boolean>>
    ) {
        if (variants.isEmpty()) {
            variants.add(baseFields.map { it.first }.toMutableList())
        } else if (variants.size == 1) {
            variants.first().addAll(0, baseFields.map { it.first })
        }

        if (variants.size == 1) {
            val psiFactory = ArendPsiFactory(project)
            val firstCCInserter = makeFirstCoClauseInserter(instanceRef.element) ?: return
            WriteCommandAction.runWriteCommandAction(editor.project) {
                for (field in variants.first()) {
                    addField(field, firstCCInserter, editor, psiFactory, allFields[field]!!)
                }
            }
            return
        }

        val className = getFullClassName()
        val defaultArguments = project.service<ArendProjectStatistics>().state.implementFieldsStatistics[className]
        var suggestDefaultOption = true
        var matchedList: List<LocatedReferable>? = null
        if (defaultArguments != null && variants[0].size == defaultArguments.size) {
            val sortedDefaultArguments = defaultArguments.sorted()
            for (variant in variants) {
                val sortedVariant = variant.map { it.textRepresentation() }.sorted()
                if (sortedVariant == sortedDefaultArguments) {
                    matchedList = variant
                }
            }
            if (matchedList == null) {
                suggestDefaultOption = false
            }
        } else {
            suggestDefaultOption = false
        }

        if (suggestDefaultOption) {
            val defaultOption = ArendBundle.message("arend.clause.implementMissing.default.option")
            val anotherOption = ArendBundle.message("arend.clause.implementMissing.another.option")
            val defaultOptionStep = object : BaseListPopupStep<String>(ArendBundle.message("arend.clause.implementMissing.question"), listOf(defaultOption, anotherOption)) {
                override fun onChosen(option: String?, finalChoice: Boolean): PopupStep<*>? {
                    if (option == defaultOption) {
                        printFields(project, editor, baseFields, matchedList!!, allFields)
                    } else if (option == anotherOption) {
                        createListOfVariants(project, editor, allFields, baseFields, variants)
                    }
                    return FINAL_CHOICE
                }
            }

            val popup = JBPopupFactory.getInstance().createListPopup(defaultOptionStep)
            popup.showInBestPositionFor(editor)
        } else {
            createListOfVariants(project, editor, allFields, baseFields, variants)
        }
    }

    private fun createListOfVariants(
        project: Project,
        editor: Editor,
        allFields: Map<LocatedReferable, Boolean>,
        baseFields: List<Pair<LocatedReferable, Boolean>>,
        variants: List<List<LocatedReferable>>
    ) {
        val fieldsToImplementStep = object : BaseListPopupStep<List<LocatedReferable>>(ArendBundle.message("arend.clause.implementMissing.question"), variants) {
            override fun onChosen(extraFields: List<LocatedReferable>?, finalChoice: Boolean): PopupStep<*>? {
                if (extraFields != null) {
                    val name = getFullClassName()
                    project.service<ArendProjectStatistics>().state.implementFieldsStatistics[name] = extraFields.map { it.textRepresentation() }
                    printFields(project, editor, baseFields, extraFields, allFields)
                }
                return FINAL_CHOICE
            }
        }

        val popup = JBPopupFactory.getInstance().createListPopup(fieldsToImplementStep)
        popup.showInBestPositionFor(editor)
    }

    private fun printFields(
        project: Project,
        editor: Editor,
        baseFields: List<Pair<LocatedReferable, Boolean>>,
        extraFields: List<LocatedReferable>,
        allFields: Map<LocatedReferable, Boolean>
    ) {
        val psiFactory = ArendPsiFactory(project)
        val firstCCInserter = makeFirstCoClauseInserter(instanceRef.element) ?: return
        val fields = baseFields.map { it.first } + extraFields
        WriteCommandAction.runWriteCommandAction(editor.project) {
            for (field in fields) {
                addField(field, firstCCInserter, editor, psiFactory, allFields[field]!!)
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        val instance = instanceRef.element ?: return

        val (groups, defaultFields) = if (instance is Abstract.ClassReferenceHolder) {
            getMinDefaultFields()
        } else {
            Pair(emptySet(), emptySet())
        }

        val allFields = fieldsToImplement.toMap()
        val baseFields = fieldsToImplement.filter { !defaultFields.contains(it.first.underlyingReferable as? PsiElement?) }
        val extraFields = fieldsToImplement.filter { defaultFields.contains(it.first.underlyingReferable as? PsiElement?) }
            .associateBy { (referable, _) -> defaultFields.find { referable.underlyingReferable == it }!! }

        var variants = mutableListOf<MutableList<LocatedReferable>>()
        for (group in groups) {
            val variant = mutableListOf<LocatedReferable>()
            for (element in group) {
                extraFields[element]?.first?.let { variant.add(it) }
            }
            variants.add(variant)
        }

        if (variants.size >= 1) {
            val minSize = variants.minBy { it.size }.size
            variants = if (minSize == 0) {
                mutableListOf()
            } else {
                variants.filter { it.size == minSize }.toMutableList()
            }
        }

        showFields(project, editor, variants, allFields, baseFields)
    }

    override fun getIcon(flags: Int) = if (needsBulb) AllIcons.Actions.IntentionBulb else null

    companion object {
        const val DEFAULT_FIELDS_LIMIT = 16
    }
}