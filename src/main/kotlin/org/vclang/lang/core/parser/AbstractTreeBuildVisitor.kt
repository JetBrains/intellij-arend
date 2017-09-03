package org.vclang.lang.core.parser

import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.Concrete
import com.jetbrains.jetpad.vclang.frontend.parser.ParseException
import com.jetbrains.jetpad.vclang.frontend.parser.ParserError
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.legacy.LegacyAbstract
import org.vclang.lang.VcFileType
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcArgument
import org.vclang.lang.core.psi.VcArgumentBinOp
import org.vclang.lang.core.psi.VcArrExpr
import org.vclang.lang.core.psi.VcAssociativity
import org.vclang.lang.core.psi.VcAtom
import org.vclang.lang.core.psi.VcAtomFieldsAcc
import org.vclang.lang.core.psi.VcAtomLevelExpr
import org.vclang.lang.core.psi.VcAtomModuleCall
import org.vclang.lang.core.psi.VcAtomPattern
import org.vclang.lang.core.psi.VcAtomPatternOrPrefix
import org.vclang.lang.core.psi.VcBinOpArg
import org.vclang.lang.core.psi.VcBinOpExpr
import org.vclang.lang.core.psi.VcBinOpLeft
import org.vclang.lang.core.psi.VcCaseExpr
import org.vclang.lang.core.psi.VcClassField
import org.vclang.lang.core.psi.VcClassImplement
import org.vclang.lang.core.psi.VcClassStat
import org.vclang.lang.core.psi.VcClassStats
import org.vclang.lang.core.psi.VcClassViewField
import org.vclang.lang.core.psi.VcClause
import org.vclang.lang.core.psi.VcClauses
import org.vclang.lang.core.psi.VcConstructor
import org.vclang.lang.core.psi.VcDataBody
import org.vclang.lang.core.psi.VcDataClauses
import org.vclang.lang.core.psi.VcDataConstructors
import org.vclang.lang.core.psi.VcDefClass
import org.vclang.lang.core.psi.VcDefClassView
import org.vclang.lang.core.psi.VcDefData
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.psi.VcDefInstance
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcElim
import org.vclang.lang.core.psi.VcExpr
import org.vclang.lang.core.psi.VcExpr0
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.VcIdentifierOrUnknown
import org.vclang.lang.core.psi.VcImplementStatements
import org.vclang.lang.core.psi.VcInfixName
import org.vclang.lang.core.psi.VcLamExpr
import org.vclang.lang.core.psi.VcLetClause
import org.vclang.lang.core.psi.VcLetExpr
import org.vclang.lang.core.psi.VcLevelExpr
import org.vclang.lang.core.psi.VcLiteral
import org.vclang.lang.core.psi.VcMaxLevelExpr
import org.vclang.lang.core.psi.VcModuleName
import org.vclang.lang.core.psi.VcNsCmd
import org.vclang.lang.core.psi.VcPattern
import org.vclang.lang.core.psi.VcPatternConstructor
import org.vclang.lang.core.psi.VcPiExpr
import org.vclang.lang.core.psi.VcPostfixName
import org.vclang.lang.core.psi.VcPrec
import org.vclang.lang.core.psi.VcPrefixName
import org.vclang.lang.core.psi.VcSetUniverseBinOp
import org.vclang.lang.core.psi.VcSigmaExpr
import org.vclang.lang.core.psi.VcStatCmd
import org.vclang.lang.core.psi.VcStatDef
import org.vclang.lang.core.psi.VcStatement
import org.vclang.lang.core.psi.VcSucLevelExpr
import org.vclang.lang.core.psi.VcTele
import org.vclang.lang.core.psi.VcTruncatedUniverseBinOp
import org.vclang.lang.core.psi.VcTuple
import org.vclang.lang.core.psi.VcTypedExpr
import org.vclang.lang.core.psi.VcUniverseAtom
import org.vclang.lang.core.psi.VcUniverseBinOp
import org.vclang.lang.core.psi.VcWhere
import org.vclang.lang.core.psi.childOfType
import org.vclang.lang.core.psi.ext.adapters.ClassDefinitionAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassFieldAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassImplementAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewFieldAdapter
import org.vclang.lang.core.psi.ext.adapters.ClassViewInstanceAdapter
import org.vclang.lang.core.psi.ext.adapters.ConstructorAdapter
import org.vclang.lang.core.psi.ext.adapters.DataDefinitionAdapter
import org.vclang.lang.core.psi.ext.adapters.DefinitionAdapter
import org.vclang.lang.core.psi.ext.adapters.FunctionDefinitionAdapter
import org.vclang.lang.core.psi.hasType
import org.vclang.lang.core.psi.isAny
import org.vclang.lang.core.psi.isEmpty
import org.vclang.lang.core.psi.isExplicit
import org.vclang.lang.core.psi.isExportCmd
import org.vclang.lang.core.psi.isHiding
import org.vclang.lang.core.psi.isImplicit
import org.vclang.lang.core.psi.isLeftAssoc
import org.vclang.lang.core.psi.isNonAssoc
import org.vclang.lang.core.psi.isOpenCmd
import org.vclang.lang.core.psi.isRightAssoc
import org.vclang.lang.core.psi.withNewContext
import java.nio.file.Paths

class AbstractTreeBuildVisitor(
        private val module: SourceId,
        private val errorReporter: ErrorReporter
) {

    fun visitModule(context: VcFile): VcFile {
        val statementsContext = context.children.filterIsInstance<VcStatement>()
        val globalStatements = visitStatements(statementsContext)
        globalStatements.let { context.globalStatements = it }
        return context
    }

    private fun visitStatements(context: List<VcStatement>): MutableList<Surrogate.Statement> =
            context.map { visitStatement(it) }.toMutableList()

    private fun visitStatement(context: VcStatement): Surrogate.Statement {
        context.statCmd?.let { return visitStatCmd(it) }
        context.statDef?.let { return visitStatDef(it) }
        error("Invalid context")
    }

    private fun visitStatCmd(context: VcStatCmd): Surrogate.NamespaceCommandStatement {
        val kind = visitNsCmd(context.nsCmd)
        val modulePath = context.nsCmdRoot?.moduleName?.let { visitModuleName(it) }
        val path = mutableListOf<String>()
        context.nsCmdRoot?.refIdentifier?.referenceName?.let { path.add(it) }
        for (fieldAcc in context.fieldAccList) {
            fieldAcc.refIdentifier?.let { path.add(it.text) }
                    ?: reportError(elementPosition(fieldAcc), "Expected a name")
        }
        val identifiers = context.refIdentifierList
        val names = if (identifiers.isNotEmpty()) identifiers.map { it.text } else null
        return Surrogate.NamespaceCommandStatement(
                elementPosition(context),
                kind,
                modulePath,
                path,
                context.isHiding,
                names
        )
    }

    private fun visitNsCmd(context: VcNsCmd): LegacyAbstract.NamespaceCommandStatement.Kind =
        when {
            context.isExportCmd -> LegacyAbstract.NamespaceCommandStatement.Kind.EXPORT
            context.isOpenCmd -> LegacyAbstract.NamespaceCommandStatement.Kind.OPEN
            else -> error("Invalid context")
        }

    private fun visitStatDef(context: VcStatDef): Surrogate.DefineStatement {
        val definition = visitDefinition(context.definition)
        return Surrogate.DefineStatement(elementPosition(definition), definition)
    }

    // Definitions

    private fun visitDefinition(context: VcDefinition): DefinitionAdapter<*> = when (context) {
        is VcDefClass -> visitDefClass(context)
        is VcDefClassView -> visitDefClassView(context)
        is VcDefData -> visitDefData(context)
        is VcDefFunction -> visitDefFunction(context)
        is VcDefInstance -> visitDefInstance(context)
        else -> {
            val childDef = context.childOfType<VcDefinition>()
            childDef?.let { return visitDefinition(it) }
            error("Invalid context")
        }
    }

    private fun visitDefClass(context: VcDefClass): ClassDefinitionAdapter {
        val name = context.defIdentifier?.text
        val polyParams = context.classTeles?.teleList?.let { visitTeles(it) }
        val superClasses = context.atomFieldsAccList.map {
            Surrogate.SuperClass(elementPosition(it), visitAtomFieldsAcc(it))
        }
        val fields = mutableListOf<ClassFieldAdapter>()
        val implementations = mutableListOf<ClassImplementAdapter>()
        val globalStatements = visitWhere(context.where)
        val instanceDefinitions = visitInstanceStatements(
                context.classStats,
                fields,
                implementations
        )

        if (context !is ClassDefinitionAdapter) error("Invalid context")
        val classDefinition = context.reconstruct(
                elementPosition(context),
                name,
                polyParams,
                superClasses,
                fields,
                implementations,
                globalStatements,
                instanceDefinitions
        )

        fields.forEach { it.setParent(classDefinition) }
        implementations.forEach { it.setParent(classDefinition) }
        instanceDefinitions.forEach {
            it.setParent(classDefinition)
            it.setNotStatic()
        }
        globalStatements
                .filterIsInstance<Surrogate.DefineStatement>()
                .forEach { it.definition.setParent(classDefinition) }

        return classDefinition
    }

    private fun visitClassStat(context: VcClassStat): Abstract.SourceNode {
        context.definition?.let {
            if (it is VcClassField) return visitClassField(it)
            if (it is VcClassImplement) return visitClassImplement(it)
        }
        context.statement?.let { return visitStatement(it) }
        error("Invalid context")
    }

    private fun visitClassField(context: VcClassField): ClassFieldAdapter {
        if (context is ClassFieldAdapter) {
            return context.reconstruct(
                    elementPosition(context),
                    context.name,
                    visitPrecedence(context.prec),
                    visitExpr(context.expr)
            )
        }
        error("Invalid context")
    }

    private fun visitClassImplement(context: VcClassImplement): ClassImplementAdapter {
        if (context !is ClassImplementAdapter) error("Invalid context")
        return context.reconstruct(
                elementPosition(context),
                context.name,
                visitExpr(context.expr)
        )
    }

    private fun visitInstanceStatements(
            context: VcClassStats?,
            fields: MutableList<ClassFieldAdapter>?,
            implementations: MutableList<ClassImplementAdapter>?
    ): List<DefinitionAdapter<*>> {
        val definitions = mutableListOf<DefinitionAdapter<*>>()
        context ?: return definitions
        for (classStatContext in context.classStatList) {
            try {
                val sourceNode = visitClassStat(classStatContext)
                val definition = if (sourceNode is Surrogate.DefineStatement) {
                    sourceNode.definition
                } else if (sourceNode is DefinitionAdapter<*>) {
                    sourceNode
                } else {
                    reportError(elementPosition(sourceNode as PsiElement), MISPLACES_DEFINITION)
                    continue
                }

                when (definition) {
                    is ClassFieldAdapter -> fields?.add(definition)
                            ?: reportError(elementPosition(definition), MISPLACES_DEFINITION)
                    is ClassImplementAdapter -> implementations?.add(definition)
                            ?: reportError(elementPosition(definition), MISPLACES_DEFINITION)
                    is FunctionDefinitionAdapter,
                    is DataDefinitionAdapter,
                    is ClassDefinitionAdapter -> definitions.add(definition)
                    else -> reportError(elementPosition(definition), MISPLACES_DEFINITION)
                }
            } catch (ignored: ParseException) {

            }

        }
        return definitions
    }

    private fun visitDefClassView(context: VcDefClassView): ClassViewAdapter {
        if (context !is ClassViewAdapter) error("Invalid context")
        val name = context.name
        val underlyingClass = visitExpr(context.expr)
        if (underlyingClass !is Surrogate.ReferenceExpression) {
            reportError(underlyingClass.position, "Expected a class")
            throw ParseException()
        }
        val classifyingFieldName = context.refIdentifier?.referenceName
        val fields = mutableListOf<ClassViewFieldAdapter>()
        val classView = context.reconstruct(
                elementPosition(context),
                name,
                underlyingClass,
                classifyingFieldName,
                fields
        )
        context.classViewFieldList.mapTo(fields) { visitClassViewField(it, classView) }
        return classView
    }

    private fun visitDefData(context: VcDefData): DataDefinitionAdapter {
        val universe: Surrogate.UniverseExpression? = context.expr?.let {
            val expr = visitExpr(it)
            if (expr is Surrogate.UniverseExpression) {
                expr
            } else {
                val message = "Specified type of the data definition is not a universe"
                reportError(elementPosition(it), message)
                null
            }
        }

        val eliminatedReferences = context.dataBody?.dataClauses?.elim?.let { visitElim(it) }
        if (context !is DataDefinitionAdapter) error("Invalid context")
        val dataDefinition = context.reconstruct(
                elementPosition(context),
                context.name,
                visitPrecedence(context.prec),
                visitTeles(context.teleList),
                eliminatedReferences,
                universe,
                mutableListOf()
        )

        visitDataBody(context.dataBody, dataDefinition)
        return dataDefinition
    }

    private fun visitDefFunction(context: VcDefFunction): FunctionDefinitionAdapter {
        val resultType = context.expr?.let { visitExpr(context.expr) }
        val body: Surrogate.FunctionBody = context.functionBody.let {
            val elimContext = it?.withElim
            if (elimContext != null) {
                return@let Surrogate.ElimFunctionBody(
                        elementPosition(elimContext),
                        visitElim(elimContext.elim),
                        visitClauses(elimContext.clauses)
                )
            }
            val withoutElimContext = it?.withoutElim
            if (withoutElimContext != null) {
                return@let Surrogate.TermFunctionBody(
                        elementPosition(withoutElimContext),
                        visitExpr(withoutElimContext.expr)
                )
            }
            error("Invalid context")
        }
        val statements = visitWhere(context.where)

        if (context !is FunctionDefinitionAdapter) error("Invalid context")
        val functionDefinition = context.reconstruct(
                elementPosition(context),
                context.name,
                visitPrecedence(context.prec),
                visitFunctionArguments(context.teleList),
                resultType,
                body,
                statements
        )

        val statementsIterator = statements.iterator()
        for (statement in statementsIterator) {
            if (statement is Surrogate.DefineStatement) {
                val definition = statement.definition
                if (definition is ClassFieldAdapter || definition is ClassImplementAdapter) {
                    reportError(elementPosition(definition), MISPLACES_DEFINITION)
                    statementsIterator.remove()
                } else {
                    definition.setParent(functionDefinition)
                }
            }
        }

        return functionDefinition
    }

    private fun visitDefInstance(context: VcDefInstance): ClassViewInstanceAdapter {
        val term = visitExpr(context.expr)
        if (term is Surrogate.NewExpression) {
            val type = term.expression
            if (type is Surrogate.ClassExtExpression) {
                if (type.baseClassExpression is Surrogate.ReferenceExpression) {
                    val name = context.name
                    if (context !is ClassViewInstanceAdapter) error("Invalid context")
                    return context.reconstruct(
                            elementPosition(context),
                            name,
                            Abstract.Precedence.DEFAULT,
                            visitFunctionArguments(context.teleList),
                            type.baseClassExpression as Surrogate.ReferenceExpression,
                            type.statements
                    )
                }
            }
        }

        reportError(elementPosition(context.expr), "Expected a class view extension")
        throw ParseException()
    }

    private fun visitDataBody(context: VcDataBody?, def: DataDefinitionAdapter) {
        context?.dataClauses?.let { return visitDataClauses(it, def) }
        context?.dataConstructors?.let { return visitDataConstructors(it, def) }
        error("Invalid context")
    }

    private fun visitDataClauses(context: VcDataClauses, def: DataDefinitionAdapter) {
        for (clauseCtx in context.constructorClauseList) {
            try {
                def.constructorClauses.add(
                        Surrogate.ConstructorClause(
                                elementPosition(clauseCtx),
                                clauseCtx.patternList.map { visitPattern(it) },
                                visitConstructors(clauseCtx.constructorList, def)
                        )
                )
            } catch (ignored: ParseException) {
            }
        }
    }

    private fun visitDataConstructors(context: VcDataConstructors, def: DataDefinitionAdapter) {
        def.constructorClauses.add(
                Surrogate.ConstructorClause(
                        elementPosition(context),
                        null,
                        visitConstructors(context.constructorList, def)
                )
        )
    }

    private fun visitElim(context: VcElim?): List<Surrogate.ReferenceExpression>? {
        return if (context != null && context.atomFieldsAccList.isNotEmpty()) {
            checkElimExpressions(context.atomFieldsAccList.map { visitAtomFieldsAcc(it) })
        } else {
            mutableListOf()
        }
    }

    private fun checkElimExpressions(
            expressions: List<Surrogate.Expression>
    ): List<Surrogate.ReferenceExpression>? {
        val predicate: (Surrogate.Expression) -> Boolean =
                { it !is Surrogate.ReferenceExpression || it.expression != null }
        expressions.firstOrNull(predicate)?.let {
            reportError(it.position, "\\elim can be applied only to a local variable")
            return null
        }
        return expressions.filterIsInstance<Surrogate.ReferenceExpression>()
    }

    private fun visitClassViewField(
            context: VcClassViewField,
            ownView: ClassViewAdapter
    ): ClassViewFieldAdapter {
        val underlyingField = context.name
        val name = context.refIdentifier?.referenceName ?: underlyingField
        if (context !is ClassViewFieldAdapter) error("Invalid context")
        return context.reconstruct(
                elementPosition(context.defIdentifier),
                name,
                visitPrecedence(context.prec),
                underlyingField,
                ownView
        )
    }

    private fun visitWhere(context: VcWhere?): MutableList<Surrogate.Statement> {
        return if (context != null && context.statementList.isNotEmpty()) {
            visitStatements(context.statementList)
        } else {
            mutableListOf()
        }
    }

    // Patterns

    private fun visitPattern(context: VcPattern): Surrogate.Pattern {
        context.atomPattern?.let { return visitAtomPattern(it) }
        context.patternConstructor?.let { return visitPatternConstructor(it) }
        error("Invalid context")
    }

    private fun visitPatternConstructor(context: VcPatternConstructor): Surrogate.Pattern {
        return if (context.atomPatternOrPrefixList.isEmpty()) {
            Surrogate.NamePattern(elementPosition(context), visitPrefix(context.prefixName))
        } else {
            Surrogate.ConstructorPattern(
                    elementPosition(context),
                    visitPrefix(context.prefixName),
                    context.atomPatternOrPrefixList.map { visitAtomPatternOrID(it) }
            )
        }
    }

    private fun visitAtomPatternOrID(context: VcAtomPatternOrPrefix): Surrogate.Pattern {
        context.atomPattern?.let { return visitAtomPattern(it) }
        val name = context.prefixName?.let { visitPrefix(it) }
        return Surrogate.NamePattern(elementPosition(context), name)
    }

    private fun visitAtomPattern(context: VcAtomPattern): Surrogate.Pattern = when {
        context.isExplicit || context.isImplicit -> {
            val pattern = visitPattern(context.pattern!!)
            pattern.isExplicit = context.isExplicit
            pattern
        }
        context.isAny -> Surrogate.NamePattern(elementPosition(context), "_")
        context.isEmpty -> Surrogate.EmptyPattern(elementPosition(context))
        else -> error("Invalid context")
    }

    private fun visitConstructors(
            context: List<VcConstructor>,
            definition: DataDefinitionAdapter
    ): List<ConstructorAdapter> = context.map {
        if (it !is ConstructorAdapter) error("Invalid context")
        val hasConditions = it.elim != null || it.clauseList.isNotEmpty()
        it.reconstruct(
                elementPosition(it),
                it.name,
                visitPrecedence(it.prec),
                definition,
                visitTeles(it.teleList),
                visitElim(it.elim),
                if (hasConditions) it.clauseList.map { visitClause(it) } else emptyList()
        )
    }

    private fun visitPrecedence(context: VcPrec?): Abstract.Precedence {
        val associativity = context?.associativity ?: return Abstract.Precedence.DEFAULT
        val priority = Integer.parseInt(context.number?.text).let {
            if (it < 1 || it > 9) {
                reportError(elementPosition(context.number), "Precedence out of range: $it")
            }
            it.coerceIn(1, 9)
        }
        return Abstract.Precedence(visitAssociativity(associativity), priority.toByte())
    }

    private fun visitAssociativity(context: VcAssociativity): Abstract.Precedence.Associativity =
        when {
            context.isLeftAssoc -> Abstract.Precedence.Associativity.LEFT_ASSOC
            context.isRightAssoc -> Abstract.Precedence.Associativity.RIGHT_ASSOC
            context.isNonAssoc -> Abstract.Precedence.Associativity.NON_ASSOC
            else -> error("Invalid context")
        }

    private fun visitExpr0(context: VcExpr0): Surrogate.Expression {
        return parseBinOpSequence(
                context.binOpLeftList,
                visitBinOpArg(context.binOpArg),
                context.postfixNameList,
                elementPosition(context)
        )
    }

    private fun visitExpr(context: VcExpr?): Surrogate.Expression = when (context) {
        is VcArrExpr -> visitArr(context)
        is VcPiExpr -> visitPi(context)
        is VcSigmaExpr -> visitSigma(context)
        is VcLamExpr -> visitLam(context)
        is VcLetExpr -> visitLet(context)
        is VcCaseExpr -> visitCase(context)
        is VcBinOpExpr -> visitBinOp(context)
        else -> {
            val childExpr = context?.childOfType<VcExpr>()
            childExpr?.let { return visitExpr(it) }
            error("Invalid context")
        }
    }

    private fun visitArr(context: VcArrExpr): Surrogate.PiExpression {
        val domain = visitExpr(context.exprList[0])
        val codomain = visitExpr(context.exprList[1])
        val arguments = listOf(Surrogate.TypeParameter(domain.position, true, domain))
        return Surrogate.PiExpression(elementPosition(context.arrow), arguments, codomain)
    }

    private fun visitBinOp(context: VcBinOpExpr): Surrogate.Expression {
        val newExpr = context.newExpr
        val position = elementPosition(context)
        val implementations = parseImplementations(
                newExpr.withNewContext,
                newExpr.implementStatements,
                position,
                visitBinOpArg(newExpr.binOpArg)
        )
        return parseBinOpSequence(
                context.binOpLeftList,
                implementations,
                context.newExpr.postfixNameList,
                position
        )
    }

    private fun visitPi(context: VcPiExpr): Surrogate.PiExpression {
        return Surrogate.PiExpression(
                elementPosition(context),
                visitTeles(context.teleList),
                visitExpr(context.expr)
        )
    }

    private fun visitSigma(context: VcSigmaExpr): Surrogate.SigmaExpression {
        val args = visitTeles(context.teleList)
        args.forEach {
            if (!it.explicit) {
                reportError(it.position, "Fields in sigma types must be explicit")
            }
        }
        return Surrogate.SigmaExpression(elementPosition(context), args)
    }

    private fun visitLam(context: VcLamExpr): Surrogate.Expression {
        return Surrogate.LamExpression(
                elementPosition(context),
                visitLamTeles(context.teleList),
                visitExpr(context.expr)
        )
    }

    private fun visitLet(context: VcLetExpr): Surrogate.LetExpression {
        val clauses = context.letClauseList.map { visitLetClause(it) }
        return Surrogate.LetExpression(elementPosition(context), clauses, visitExpr(context.expr))
    }

    private fun visitCase(context: VcCaseExpr): Surrogate.Expression {
        val elimExprs = context.expr0List.map { visitExpr0(it) }
        val clauses = context.clauseList.map { visitClause(it) }
        return Surrogate.CaseExpression(elementPosition(context), elimExprs, clauses)
    }

    private fun visitClauses(context: VcClauses): List<Surrogate.FunctionClause> =
            context.clauseList.map { visitClause(it) }

    private fun visitLetClause(context: VcLetClause): Surrogate.LetClause {
        val name = context.name
        val arguments = visitLamTeles(context.teleList)
        val resultType = context.typeAnnotation?.let { visitExpr(it.expr) }
        return Surrogate.LetClause(
                elementPosition(context),
                name,
                arguments,
                resultType,
                visitExpr(context.expr)
        )
    }

    private fun visitClause(context: VcClause): Surrogate.FunctionClause {
        val patterns = context.patternList.map { visitPattern(it) }
        val expression = context.expr?.let { visitExpr(it) }
        return Surrogate.FunctionClause(elementPosition(context), patterns, expression)
    }

    private fun visitLevelExpr(context: VcLevelExpr): Surrogate.LevelExpression? {
        context.atomLevelExpr?.let { return visitAtomLevelExpr(it) }
        context.maxLevelExpr?.let { return visitMaxLevelExpr(it) }
        context.sucLevelExpr?.let { return visitSucLevelExpr(it) }
        error("Invalid context")
    }

    private fun visitAtomLevelExpr(context: VcAtomLevelExpr?): Surrogate.LevelExpression? {
        context?.lpKw?.let { return Surrogate.PLevelExpression(elementPosition(context)) }
        context?.lhKw?.let { return Surrogate.HLevelExpression(elementPosition(context)) }
        context?.number?.let {
            val number = Integer.parseInt(it.text)
            return Surrogate.NumberLevelExpression(elementPosition(it), number)
        }
        context?.levelExpr?.let { return visitLevelExpr(it) }
        error("Invalid context")
    }

    private fun visitSucLevelExpr(context: VcSucLevelExpr): Surrogate.SucLevelExpression {
        return Surrogate.SucLevelExpression(
                elementPosition(context),
                visitAtomLevelExpr(context.atomLevelExpr)
        )
    }

    private fun visitMaxLevelExpr(context: VcMaxLevelExpr): Surrogate.MaxLevelExpression {
        return Surrogate.MaxLevelExpression(
                elementPosition(context),
                visitAtomLevelExpr(context.atomLevelExprList[0]),
                visitAtomLevelExpr(context.atomLevelExprList[1])
        )
    }

    private fun visitBinOpArg(context: VcBinOpArg?): Surrogate.Expression {
        context?.argumentBinOp?.let { return visitBinOpArgument(it) }
        context?.universeBinOp?.let { return visitUniverse(it) }
        context?.setUniverseBinOp?.let { return visitSetUniverse(it) }
        context?.truncatedUniverseBinOp?.let { return visitTruncatedUniverse(it) }
        error("Invalid context")
    }

    private fun visitBinOpArgument(context: VcArgumentBinOp): Surrogate.Expression =
            visitArguments(visitAtomFieldsAcc(context.atomFieldsAcc), context.argumentList)

    private fun visitUniverse(context: VcUniverseBinOp): Surrogate.UniverseExpression {
        val text = context.universe.text.substring("\\Type".length)

        var lp: Surrogate.LevelExpression? = null
        if (text.isNotEmpty()) {
            val position = elementPosition(context.universe)
            val number = Integer.parseInt(text)
            lp = Surrogate.NumberLevelExpression(position, number)
        }

        var lh: Surrogate.LevelExpression? = null
        if (context.atomLevelExprList.size >= 1) {
            val firstExpr = context.atomLevelExprList[0]
            val level0 = visitAtomLevelExpr(firstExpr)
            if (lp == null) {
                lp = level0
            } else {
                lh = level0
            }

            if (context.atomLevelExprList.size >= 2) {
                val secondExpr = context.atomLevelExprList[1]
                val level1 = visitAtomLevelExpr(secondExpr)
                if (lh == null) {
                    lh = level1
                } else {
                    reportError(elementPosition(secondExpr), "h-level is already specified")
                }
            }
        }

        return Surrogate.UniverseExpression(elementPosition(context), lp, lh)
    }

    private fun visitSetUniverse(context: VcSetUniverseBinOp): Surrogate.UniverseExpression {
        val text = context.set.text.substring("\\Set".length)
        val pLevel: Surrogate.LevelExpression? = if (text.isEmpty()) {
            context.atomLevelExpr?.let { visitAtomLevelExpr(it) }
        } else {
            if (context.atomLevelExpr != null) {
                reportError(elementPosition(context.atomLevelExpr), "p-level is already specified")
            }
            val number = Integer.parseInt(text)
            Surrogate.NumberLevelExpression(elementPosition(context.set), number)
        }
        val position = elementPosition(context)
        val numberLevel = Surrogate.NumberLevelExpression(position, 0)
        return Surrogate.UniverseExpression(position, pLevel, numberLevel)
    }

    private fun visitTruncatedUniverse(
        context: VcTruncatedUniverseBinOp
    ): Surrogate.UniverseExpression {
        val text = context.truncatedUniverse.text.let {
            it.substring(it.indexOf('-') + "-Type".length)
        }
        val pLevel: Surrogate.LevelExpression? = if (text.isEmpty()) {
            context.atomLevelExpr?.let { visitAtomLevelExpr(it) }
        } else {
            if (context.atomLevelExpr != null) {
                reportError(elementPosition(context.atomLevelExpr), "p-level is already specified")
            }
            val number = Integer.parseInt(text)
            Surrogate.NumberLevelExpression(elementPosition(context.truncatedUniverse), number)
        }
        val truncatedUniverse = parseTruncatedUniverse(context.truncatedUniverse)
        return Surrogate.UniverseExpression(elementPosition(context), pLevel, truncatedUniverse)
    }

    private fun parseBinOpSequence(
            context: List<VcBinOpLeft>,
            expression: Surrogate.Expression,
            postfixContexts: List<VcPostfixName>,
            position: Surrogate.Position
    ): Surrogate.Expression {
        var left: Surrogate.Expression? = null
        var binOp: Surrogate.ReferenceExpression? = null
        val sequence = mutableListOf<Abstract.BinOpSequenceElem>()
        for (leftContext in context) {
            val newExpr = leftContext.newExpr
            val expr = parseImplementations(
                    newExpr.withNewContext,
                    newExpr.implementStatements,
                    position,
                    visitBinOpArg(newExpr.binOpArg)
            )

            if (left == null) {
                left = expr
            } else {
                sequence.add(Abstract.BinOpSequenceElem(binOp!!, expr))
            }

            leftContext.newExpr.postfixNameList
                    .map { Surrogate.ReferenceExpression(
                        elementPosition(it),
                        null,
                        visitPostfix(it)
                    ) }
                    .mapTo(sequence) { Abstract.BinOpSequenceElem(it, null) }

            val name = visitInfix(leftContext.infixName)
            binOp = Surrogate.ReferenceExpression(
                elementPosition(leftContext.infixName),
                null,
                name
            )
        }

        if (left == null) {
            left = expression
        } else {
            sequence.add(Abstract.BinOpSequenceElem(binOp!!, expression))
        }

        postfixContexts
                .map { Surrogate.ReferenceExpression(elementPosition(it), null, visitPostfix(it)) }
                .mapTo(sequence) { Abstract.BinOpSequenceElem(it, null) }

        return if (sequence.isNotEmpty()) {
            Surrogate.BinOpSequenceExpression(position, left, sequence)
        } else {
            left
        }
    }

    private fun visitModuleName(context: VcModuleName): List<String> {
        val lastModuleNamePart = context.moduleNamePartList.lastOrNull()
        val module = lastModuleNamePart?.reference?.resolve() as? VcFile
        val modulePath = Paths.get(
            module?.virtualFile?.path?.removeSuffix('.' + VcFileType.defaultExtension)
        )
        val base = Paths.get(context.project.basePath)
        return base.relativize(modulePath).toList().map { it.toString() }
    }

    private fun visitAtom(expr: VcAtom): Surrogate.Expression {
        expr.atomModuleCall?.let { return visitAtomModuleCall(it) }
        expr.literal?.let { return visitLiteral(it) }
        expr.tuple?.let { return visitTuple(it) }
        expr.number?.let { return visitAtomNumber(it) }
        error("Invalid context")
    }

    private fun visitAtomModuleCall(context: VcAtomModuleCall): Surrogate.ModuleCallExpression {
        return Surrogate.ModuleCallExpression(
                elementPosition(context),
                visitModuleName(context.moduleName)
        )
    }

    private fun visitTuple(context: VcTuple): Surrogate.Expression {
        return if (context.exprList.size == 1) {
            visitExpr(context.exprList.first())
        } else {
            val fields = context.exprList.map { visitExpr(it) }
            Surrogate.TupleExpression(elementPosition(context), fields)
        }
    }

    private fun visitAtomNumber(context: PsiElement): Surrogate.NumericLiteral {
        val number = Integer.parseInt(context.text)
        return Surrogate.NumericLiteral(elementPosition(context), number)
    }

    private fun visitAtomFieldsAcc(context: VcAtomFieldsAcc?): Surrogate.Expression {
        context ?: error("Invalid context")
        var expr = visitAtom(context.atom)
        for (acc in context.fieldAccList) {
            expr = when {
                acc.refIdentifier != null -> Surrogate.ReferenceExpression(
                    elementPosition(acc),
                    expr,
                    acc.refIdentifier?.referenceName
                )
                acc.number != null -> {
                    val field = Integer.parseInt(acc.number?.text) - 1
                    Surrogate.ProjExpression(
                        elementPosition(acc),
                        expr,
                        field
                    )
                }
                else -> error("Invalid context")
            }
        }
        return expr
    }

    private fun parseImplementations(
            withNewContext: Boolean,
            context: VcImplementStatements?,
            position: Surrogate.Position,
            expr: Surrogate.Expression
    ): Surrogate.Expression {
        var implementations = expr

        if (context != null) {
            val implementStatements = context.implementStatementList.map {
                Surrogate.ClassFieldImpl(
                        elementPosition(it.refIdentifier),
                        it.refIdentifier.referenceName,
                        visitExpr(it.expr)
                )
            }
            implementations = Surrogate.ClassExtExpression(
                    position,
                    implementations,
                    implementStatements
            )
        }

        if (withNewContext) {
            implementations = Surrogate.NewExpression(position, implementations)
        }

        return implementations
    }

    private fun visitArguments(
            expr: Surrogate.Expression,
            context: List<VcArgument>
    ): Surrogate.Expression {
        var appExpr = expr
        for (arg in context) {
            val expr1 = when {
                arg.atomFieldsAcc != null -> visitAtomFieldsAcc(arg.atomFieldsAcc)
                arg.expr != null -> visitExpr(arg.expr)
                arg.universeAtom != null -> visitUniverseAtom(arg.universeAtom)
                else -> error("Invalid context")
            }
            val argumentExpr = Surrogate.Argument(expr1, arg.expr == null)
            appExpr = Surrogate.AppExpression(expr.position, appExpr, argumentExpr)
        }
        return appExpr
    }

    private fun visitLiteral(context: VcLiteral): Surrogate.Expression {
        context.prefixName?.let {
            return Surrogate.ReferenceExpression(elementPosition(context), null, visitPrefix(it))
        }
        context.propKw?.let {
            val position = elementPosition(context)
            return Surrogate.UniverseExpression(
                    position,
                    Surrogate.NumberLevelExpression(position, 0),
                    Surrogate.NumberLevelExpression(position, -1)
            )
        }
        context.underscore?.let { return Surrogate.InferHoleExpression(elementPosition(context)) }
        context.goal?.let {
            return Surrogate.GoalExpression(
                    elementPosition(context),
                    it.name,
                    it.expr?.let { visitExpr(it) }
            )
        }
        error("Invalid context")
    }

    private fun visitUniTruncatedUniverse(context: VcUniverseAtom): Surrogate.UniverseExpression {
        val truncatedUniverse = context.truncatedUniverse ?: error("Invalid context")
        val text = truncatedUniverse.text.let { it.substring(it.indexOf('-') + "-Type".length) }
        val pLevel = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Surrogate.NumberLevelExpression(elementPosition(truncatedUniverse), number)
        } else {
            null
        }
        return Surrogate.UniverseExpression(
                elementPosition(context),
                pLevel,
                parseTruncatedUniverse(truncatedUniverse)
        )
    }

    private fun parseTruncatedUniverse(context: PsiElement): Surrogate.LevelExpression {
        val universe = context.text
        if (universe[1] == 'o') {
            return Surrogate.InfLevelExpression(elementPosition(context))
        }
        val number = Integer.parseInt(universe.substring(1, universe.indexOf('-')))
        return Surrogate.NumberLevelExpression(elementPosition(context), number)
    }

    private fun visitUniverseAtom(context: VcUniverseAtom?): Surrogate.UniverseExpression {
        context?.set?.let { return visitUniSetUniverse(context) }
        context?.truncatedUniverse?.let { return visitUniTruncatedUniverse(context) }
        context?.universe?.let { return visitUniUniverse(context) }
        error("Invalid context")
    }

    private fun visitUniUniverse(context: VcUniverseAtom): Surrogate.UniverseExpression {
        val universe = context.universe ?: error("Invalid context")
        val text = universe.text.substring("\\Type".length)
        val lp = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Surrogate.NumberLevelExpression(elementPosition(universe), number)
        } else {
            null
        }
        return Surrogate.UniverseExpression(elementPosition(context), lp, null)
    }

    private fun visitUniSetUniverse(context: VcUniverseAtom): Surrogate.UniverseExpression {
        val text = context.set?.text?.substring("\\Set".length) ?: error("Invalid context")
        val pLevel = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Surrogate.NumberLevelExpression(elementPosition(context.set), number)
        } else {
            null
        }
        val position = elementPosition(context)
        return Surrogate.UniverseExpression(
                position,
                pLevel,
                Surrogate.NumberLevelExpression(position, 0)
        )
    }

    private fun visitLamTele(context: VcTele): List<Surrogate.Parameter>? {
        val parameters = mutableListOf<Surrogate.Parameter>()
        if (context.isExplicit || context.isImplicit) {
            val explicit = context.isExplicit
            val typedExpr = context.typedExpr!!
            val varsExpr1 = typedExpr.identifierOrUnknownList
            val varsExpr2 = typedExpr.expr

            val typeExpr = if (typedExpr.hasType) visitExpr(typedExpr.expr) else null
            val vars = if (typedExpr.hasType) varsExpr1.map { getVar(it) } else getVars(varsExpr2)

            if (typeExpr == null) {
                vars.mapTo(parameters) { Surrogate.NameParameter(it?.position, explicit, it?.name) }
            } else {
                val parameter = Surrogate.TelescopeParameter(
                        elementPosition(context),
                        explicit,
                        vars,
                        typeExpr
                )
                parameters.add(parameter)
            }
        } else {
            var ok = context.literal != null
            if (ok) {
                val literalContext = context.literal
                if (literalContext?.prefixName != null || literalContext?.underscore != null) {
                    val name = literalContext.prefixName?.let { visitPrefix(it) }
                    val parameter = Surrogate.NameParameter(
                            elementPosition(literalContext),
                            true,
                            name
                    )
                    parameters.add(parameter)
                } else {
                    ok = false
                }
            }
            if (!ok) {
                reportError(elementPosition(context), "Unexpected token, expected an identifier")
                throw ParseException()
            }
        }
        return parameters
    }

    private fun visitLamTeles(context: List<VcTele>): List<Surrogate.Parameter> =
            context.mapNotNull { visitLamTele(it) }.flatten()

    private fun visitTeles(context: List<VcTele>): List<Surrogate.TypeParameter> {
        val parameters = mutableListOf<Surrogate.TypeParameter>()
        for (tele in context) {
            val explicit = !tele.isImplicit
            var typedExpr: VcTypedExpr?
            if (explicit) {
                if (tele.isExplicit) {
                    typedExpr = tele.typedExpr
                } else if (tele.literal != null) {
                    val literal = tele.literal?.let { visitLiteral(it) }
                    parameters.add(Surrogate.TypeParameter(true, literal))
                    continue
                } else if (tele.universeAtom != null) {
                    val universeAtom = tele.universeAtom?.let { visitUniverseAtom(it) }
                    parameters.add(Surrogate.TypeParameter(true, universeAtom))
                    continue
                } else {
                    error("Invalid context")
                }
            } else {
                typedExpr = tele.typedExpr
            }

            if (typedExpr != null && typedExpr.hasType) {
                val vars = typedExpr.identifierOrUnknownList.map { getVar(it) }
                val parameter = Surrogate.TelescopeParameter(
                        elementPosition(tele),
                        explicit,
                        vars,
                        visitExpr(typedExpr.expr)
                )
                parameters.add(parameter)
            } else {
                val parameter = Surrogate.TypeParameter(explicit, visitExpr(typedExpr?.expr))
                parameters.add(parameter)
            }
        }
        return parameters
    }

    private fun visitFunctionArguments(context: List<VcTele>): List<Surrogate.Parameter> {
        val arguments = mutableListOf<Surrogate.Parameter>()
        for (tele in context) {
            val args = visitLamTele(tele)
            if (args != null && args.isNotEmpty()) {
                if (args.first() is Surrogate.TelescopeParameter) {
                    arguments.add(args.first())
                } else {
                    reportError(elementPosition(tele), "Expected a typed variable")
                }
            }
        }
        return arguments
    }

    // Utils

    private fun elementPosition(element: PsiElement?): Surrogate.Position =
            Surrogate.Position(module, element)

    private fun getVar(context: VcAtomFieldsAcc): Surrogate.LocalVariable? =
            if (context.fieldAccList.isEmpty()) getVar(context.atom.literal) else null

    private fun getVar(context: VcLiteral?): Surrogate.LocalVariable? {
        context?.prefixName?.let { return Surrogate.LocalVariable(elementPosition(it), it.text) }
        context?.underscore?.let { return Surrogate.LocalVariable(elementPosition(it), null) }
        error("Invalid context")
    }

    private fun getVar(context: VcIdentifierOrUnknown): Surrogate.LocalVariable? =
            Surrogate.LocalVariable(elementPosition(context), context.text)

    private fun getVarsNull(context: VcExpr): List<Surrogate.LocalVariable>? {
        if (context !is VcBinOpExpr
                || context.binOpLeftList.isNotEmpty()
                || context.newExpr.binOpArg?.argumentBinOp == null
                || context.newExpr.withNewContext
                || context.newExpr.implementStatements != null
                ) {
            return null
        }

        val argumentBinOp = context.newExpr.binOpArg?.argumentBinOp ?: return null
        val firstArg = argumentBinOp.atomFieldsAcc.let { getVar(it) } ?: return null
        val result = mutableListOf(firstArg)
        for (argument in argumentBinOp.argumentList) {
            when {
                argument.atomFieldsAcc != null -> {
                    val arg = argument.atomFieldsAcc?.let { getVar(it) } ?: return null
                    result.add(arg)
                }
                argument.expr != null -> {
                    val arguments = argument.expr?.let { getVarsNull(it) } ?: return null
                    result.addAll(arguments)
                }
                else -> return null
            }
        }
        return result
    }

    private fun getVars(context: VcExpr): List<Surrogate.LocalVariable> {
        val result = getVarsNull(context)
        result?.let { return it }
        reportError(elementPosition(context), "Expected a list of variables")
        throw ParseException()
    }

    private fun visitPrefix(prefix: VcPrefixName): String {
        prefix.prefix?.let { return it.text }
        prefix.prefixInfix?.let { return it.text.removePrefix("`") }
        error("Invalid context")
    }

    private fun visitInfix(infix: VcInfixName): String {
        infix.infix?.let { return it.text }
        infix.infixPrefix?.let { return it.text.removePrefix("`") }
        error("Invalid context")
    }

    private fun visitPostfix(postfix: VcPostfixName): String {
        postfix.postfixInfix?.let { return it.text.removeSuffix("`") }
        postfix.postfixPrefix?.let { return it.text.removeSuffix("`") }
        error("Invalid context")
    }

    // Errors

    private val MISPLACES_DEFINITION = "This definition is not allowed here"

    private fun reportError(position: Surrogate.Position, message: String) {
        val concretePosition = Concrete.Position(position.module, 0, 0)
        val error = ParserError(concretePosition, message)
        errorReporter.report(error)
    }
}
