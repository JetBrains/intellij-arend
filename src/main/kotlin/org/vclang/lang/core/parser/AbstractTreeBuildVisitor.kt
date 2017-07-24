package org.vclang.lang.core.parser

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.Concrete
import com.jetbrains.jetpad.vclang.frontend.parser.ParseException
import com.jetbrains.jetpad.vclang.frontend.parser.ParserError
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.psi.*
import java.util.*

class AbstractTreeBuildVisitor(
        private val module: SourceId,
        private val errorReporter: ErrorReporter
) {

    fun visitStatements(context: VcStatements): List<Concrete.Statement> =
            visitStatementList(context.statementList)

    private fun visitStatementList(context: List<VcStatement>): MutableList<Concrete.Statement> =
            context.map { visitStatement(it) }.filterNotNull().toMutableList()

    fun visitStatement(context: VcStatement): Concrete.Statement {
        context.statCmd?.let { return visitStatCmd(it) }
        context.statDef?.let { return visitStatDef(it) }
        throw IllegalStateException()
    }

    fun visitStatCmd(context: VcStatCmd): Concrete.NamespaceCommandStatement {
        val kind = visitNsCmd(context.nsCmd)
        val modulePath = context.nsCmdRoot?.modulePath?.let { visitModulePath(it) }
        val path = mutableListOf<String>()
        context.nsCmdRoot?.identifier?.let { path.add(visitName(it)) }
        for (acc in context.fieldAccList) {
            if (acc is VcIdentifier) {
                path.add(visitName(acc.identifier))
            } else {
                reportError(elementPosition(acc), "Expected a name")
            }
        }
        val names = if (context.identifierList.isNotEmpty()) {
            context.identifierList.map { visitName(it) }
        } else {
            null
        }
        return Concrete.NamespaceCommandStatement(
                elementPosition(context),
                kind,
                modulePath,
                path,
                context.hidingKw != null,
                names
        )
    }

    fun visitNsCmd(context: VcNsCmd): Concrete.NamespaceCommandStatement.Kind {
        return if (context.openKw != null) {
            Concrete.NamespaceCommandStatement.Kind.OPEN
        } else {
            Concrete.NamespaceCommandStatement.Kind.EXPORT
        }
    }

    fun visitStatDef(context: VcStatDef): Concrete.DefineStatement {
        val definition = visitDefinition(context.definition)
        return Concrete.DefineStatement(definition.position, definition)
    }

    // Definitions

    fun visitDefinition(context: VcDefinition): Concrete.Definition {
        context.defAbstract?.let { return visitDefAbstract(it) }
        context.defClass?.let { return visitDefClass(it) }
        context.defClassView?.let { return visitDefClassView(it) }
        context.defData?.let { return visitDefData(it) }
        context.defFunction?.let { return visitDefFunction(it) }
        context.defImplement?.let { return visitDefImplement(it) }
        context.defInstance?.let { return visitDefInstance(it) }
        throw IllegalStateException()
    }

    fun visitDefAbstract(context: VcDefAbstract): Concrete.ClassField {
        return Concrete.ClassField(
                elementPosition(context),
                visitName(context.identifier),
                visitPrecedence(context.prec),
                mutableListOf(),
                visitExpr(context.expr)
        )
    }


    fun visitDefClass(context: VcDefClass): Concrete.ClassDefinition {
        val name = context.id?.text
        val polyParams = visitTeles(context.teleList)
        val superClasses = context.expr0List.map {
            Concrete.SuperClass(elementPosition(it), visitExpr0(it))
        }
        val fields = mutableListOf<Concrete.ClassField>()
        val implementations = mutableListOf<Concrete.Implementation>()
        val globalStatements = visitWhere(context.where)
        val instanceDefinitions = visitInstanceStatements(
                context.statementList,
                fields,
                implementations
        )
        val classDefinition = Concrete.ClassDefinition(
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
            it.setIsStatic(false)
        }
        globalStatements
                .filterIsInstance<Concrete.DefineStatement>()
                .forEach { it.definition.setParent(classDefinition) }

        return classDefinition
    }

    private fun visitInstanceStatements(
            context: List<VcStatement>,
            fields: MutableList<Concrete.ClassField>,
            implementations: MutableList<Concrete.Implementation>
    ): List<Concrete.Definition> {
        val definitions = mutableListOf<Concrete.Definition>()
        for (statementContext in context) {
            try {
                val statement = visitStatement(statementContext)
                if (statement is Concrete.DefineStatement) {
                    val definition = statement.definition
                    when (definition) {
                        is Concrete.ClassField -> fields.add(definition)
                        is Concrete.Implementation -> implementations.add(definition)
                        is Concrete.FunctionDefinition,
                        is Concrete.DataDefinition,
                        is Concrete.ClassDefinition -> definitions.add(definition)
                        else -> reportError(definition.position, MISPLACES_DEFINITION)
                    }
                } else {
                    reportError(statement.position, MISPLACES_DEFINITION)
                }
            } catch (ignored: ParseException) {
            }
        }
        return definitions
    }

    fun visitDefClassView(context: VcDefClassView): Concrete.ClassView {
        val name = context.id?.text
        val underlyingClass = visitExpr(context.expr)
        if (underlyingClass !is Concrete.ReferenceExpression) {
            reportError(underlyingClass.position, "Expected a class")
            throw ParseException()
        }
        val fields = mutableListOf<Concrete.ClassViewField>()
        val classView = Concrete.ClassView(
                elementPosition(context),
                name,
                underlyingClass,
                visitName(context.identifier),
                fields
        )
        context.classViewFieldList.mapTo(fields) { visitClassViewField(it, classView) }
        return classView
    }

    fun visitDefData(context: VcDefData): Concrete.DataDefinition {
        val eliminatedReferences = context.dataBody?.dataClauses?.elim?.let { visitElim(it) }
        val universe = context.expr?.let { visitExpr(it) }
        val dataDefinition = Concrete.DataDefinition(
                elementPosition(context),
                visitName(context.identifier),
                visitPrecedence(context.prec),
                visitTeles(context.teleList),
                eliminatedReferences,
                context.isTruncated,
                universe,
                mutableListOf()
        )
        visitDataBody(context.dataBody, dataDefinition)
        return dataDefinition
    }

    fun visitDefFunction(context: VcDefFunction): Concrete.FunctionDefinition {
        val resultType = context.expr?.let { visitExpr(context.expr) }
        val body: Concrete.FunctionBody = context.functionBody.let {
            val elimContext = it?.withElim
            if (elimContext != null) {
                return@let Concrete.ElimFunctionBody(
                        elementPosition(elimContext),
                        visitElim(elimContext.elim),
                        visitClauses(elimContext.clauses)
                )
            }
            val withoutElimContext = it?.withoutElim
            if (withoutElimContext != null) {
                return@let Concrete.TermFunctionBody(
                        elementPosition(withoutElimContext),
                        visitExpr(withoutElimContext.expr)
                )
            }
            throw IllegalStateException()
        }
        val statements = visitWhere(context.where)
        val functionDefinition = Concrete.FunctionDefinition(
                elementPosition(context),
                visitName(context.identifier),
                visitPrecedence(context.prec),
                visitFunctionArguments(context.teleList),
                resultType,
                body,
                statements
        )

        val statementsIterator = statements.iterator()
        for (statement in statementsIterator) {
            if (statement is Concrete.DefineStatement) {
                val definition = statement.definition
                if (definition is Concrete.ClassField || definition is Concrete.Implementation) {
                    reportError(definition.position, MISPLACES_DEFINITION)
                    statementsIterator.remove()
                } else {
                    definition.setParent(functionDefinition)
                }
            }
        }

        return functionDefinition
    }

    fun visitDefImplement(context: VcDefImplement): Concrete.Implementation {
        return Concrete.Implementation(
                elementPosition(context),
                visitName(context.identifier),
                visitExpr(context.expr)
        )
    }

    fun visitDefInstance(context: VcDefInstance): Concrete.ClassViewInstance {
        val term = visitExpr(context.expr)
        if (term is Concrete.NewExpression) {
            val type = term.expression
            if (type is Concrete.ClassExtExpression) {
                if (type.baseClassExpression is Concrete.ReferenceExpression) {
                    val name = context.id?.text
                    return Concrete.ClassViewInstance(
                            elementPosition(context),
                            context.defaultKw != null,
                            name,
                            Abstract.Precedence.DEFAULT,
                            visitFunctionArguments(context.teleList),
                            type.baseClassExpression as Concrete.ReferenceExpression,
                            type.statements
                    )
                }
            }
        }

        reportError(elementPosition(context.expr), "Expected a class view extension")
        throw ParseException()
    }

    fun visitDataBody(context: VcDataBody?, def: Concrete.DataDefinition) {
        context?.dataClauses?.let { return visitDataClauses(it, def) }
        context?.dataConstructors?.let { return visitDataConstructors(it, def) }
        throw IllegalStateException()
    }

    fun visitDataClauses(context: VcDataClauses, def: Concrete.DataDefinition) {
        for (clauseCtx in context.constructorClauseList) {
            try {
                def.constructorClauses.add(
                        Concrete.ConstructorClause(
                                elementPosition(clauseCtx),
                                clauseCtx.patternList.map { visitPattern(it) },
                                visitConstructors(clauseCtx.constructorList, def)
                        )
                )
            } catch (ignored: ParseException) {
            }
        }
    }

    fun visitDataConstructors(context: VcDataConstructors, def: Concrete.DataDefinition) {
        def.constructorClauses.add(
                Concrete.ConstructorClause(
                        elementPosition(context),
                        null,
                        visitConstructors(context.constructorList, def)
                )
        )
    }

    fun visitElim(context: VcElim?): List<Concrete.ReferenceExpression>? {
        return if (context != null && context.expr0List.isNotEmpty()) {
            checkElimExpressions(context.expr0List.map { visitExpr0(it) })
        } else {
            mutableListOf()
        }
    }

    private fun checkElimExpressions(
            expressions: List<Concrete.Expression>
    ): List<Concrete.ReferenceExpression>? {
        val predicate: (Concrete.Expression) -> Boolean =
                { it !is Concrete.ReferenceExpression || it.expression != null }
        expressions.firstOrNull(predicate)?.let {
            reportError(it.position, "\\elim can be applied only to a local variable")
            return null
        }
        return expressions.filterIsInstance<Concrete.ReferenceExpression>()
    }

    fun visitClassViewField(
            context: VcClassViewField,
            ownView: Concrete.ClassView
    ): Concrete.ClassViewField {
        val underlyingField = visitName(context.identifierList[0])
        val name = if (context.identifierList.size > 1) {
            visitName(context.identifierList[1])
        } else {
            underlyingField
        }
        return Concrete.ClassViewField(
                elementPosition(context.identifierList[0]),
                name,
                visitPrecedence(context.prec),
                underlyingField,
                ownView
        )
    }

    fun visitWhere(context: VcWhere?): MutableList<Concrete.Statement> {
        return if (context != null && context.statementList.isNotEmpty()) {
            visitStatementList(context.statementList)
        } else {
            mutableListOf()
        }
    }

    // Patterns

    fun visitPattern(context: VcPattern): Concrete.Pattern {
        context.atomPattern?.let { return visitAtomPattern(it) }
        context.patternConstructor?.let { return visitPatternConstructor(it) }
        throw IllegalStateException()
    }

    fun visitPatternConstructor(context: VcPatternConstructor): Concrete.Pattern {
        context.identifier.id?.let {
            if (context.atomPatternOrIDList.isEmpty()) {
                val variable = Concrete.LocalVariable(elementPosition(it), it.text)
                return Concrete.NamePattern(elementPosition(context), variable)
            }
        }
        return Concrete.ConstructorPattern(
                elementPosition(context),
                visitName(context.identifier),
                context.atomPatternOrIDList.map { visitAtomPatternOrID(it) }
        )
    }

    fun visitAtomPatternOrID(context: VcAtomPatternOrID): Concrete.Pattern {
        context.atomPattern?.let { return visitAtomPattern(it) }
        val position = elementPosition(context)
        val name = context.id?.text
        val referent = Concrete.LocalVariable(position, name)
        return Concrete.NamePattern(position, referent)
    }

    fun visitAtomPattern(context: VcAtomPattern): Concrete.Pattern {
        context.pattern?.let {
            val pattern = visitPattern(it)
            if (context.lbrace != null) {
                pattern.isExplicit = false
            }
            return pattern
        }

        val position = elementPosition(context)
        return if (context.underscore != null) {
            val referent = Concrete.LocalVariable(position, "_")
            Concrete.NamePattern(position, referent)
        } else {
            Concrete.EmptyPattern(position)
        }
    }

    private fun visitConstructors(
            context: List<VcConstructor>,
            def: Concrete.DataDefinition
    ): List<Concrete.Constructor> = context.map {
        val hasConditions = it.elim != null || it.clauseList.isNotEmpty()
        Concrete.Constructor(
                elementPosition(it),
                visitName(it.identifier),
                visitPrecedence(it.prec),
                def,
                visitTeles(it.teleList),
                if (hasConditions) visitElim(it.elim) else null,
                if (hasConditions) it.clauseList.map { visitClause(it) } else null
        )
    }

    fun visitPrecedence(context: VcPrec?): Abstract.Precedence {
        val associativity = context?.associativity ?: return Abstract.Precedence.DEFAULT
        val priority = Integer.parseInt(context.number?.text).let {
            if (it < 1 || it > 9) {
                reportError(elementPosition(context.number), "Precedence out of range: $it")
            }
            it.coerceIn(1, 9)
        }
        return Abstract.Precedence(visitAssociativity(associativity), priority.toByte())
    }

    fun visitAssociativity(context: VcAssociativity): Abstract.Precedence.Associativity {
        return when {
            context.leftAssocKw != null -> Abstract.Precedence.Associativity.LEFT_ASSOC
            context.rightAssocKw != null -> Abstract.Precedence.Associativity.RIGHT_ASSOC
            else -> Abstract.Precedence.Associativity.NON_ASSOC
        }
    }

    fun visitName(context: VcIdentifier?): String {
        context?.id?.let { return it.text }
        context?.binOp?.let { return it.text }
        throw IllegalStateException()
    }

    fun visitExpr0(context: VcExpr0): Concrete.Expression {
        return parseBinOpSequence(
                context.binOpLeftList,
                visitBinOpArg(context.binOpArg),
                elementPosition(context)
        )
    }

    fun visitExpr(expr: VcExpr?): Concrete.Expression {
        return when (expr) {
            is VcArrExpr -> visitArr(expr)
            is VcPiExpr -> visitPi(expr)
            is VcSigmaExpr -> visitSigma(expr)
            is VcLamExpr -> visitLam(expr)
            is VcLetExpr -> visitLet(expr)
            is VcCaseExpr -> visitCase(expr)
            is VcBinOpExpr -> visitBinOp(expr)
            else -> {
                val childExpr = expr?.let { PsiTreeUtil.findChildOfType(it, VcExpr::class.java) }
                childExpr?.let { return visitExpr(it) }
                throw IllegalStateException()
            }
        }
    }

    fun visitArr(context: VcArrExpr): Concrete.PiExpression {
        val domain = visitExpr(context.exprList[0])
        val codomain = visitExpr(context.exprList[1])
        val arguments = listOf(Concrete.TypeArgument(domain.position, true, domain))
        return Concrete.PiExpression(elementPosition(context.arrow), arguments, codomain)
    }

    fun visitBinOp(context: VcBinOpExpr): Concrete.Expression {
        val newExpr = context.newExpr
        val position = elementPosition(context)
        val implementations = parseImplementations(
                newExpr.newKw != null,
                newExpr.implementStatements,
                position,
                visitBinOpArg(newExpr.binOpArg)
        )
        return parseBinOpSequence(context.binOpLeftList, implementations, position)
    }

    fun visitPi(context: VcPiExpr): Concrete.PiExpression {
        return Concrete.PiExpression(
                elementPosition(context),
                visitTeles(context.teleList),
                visitExpr(context.expr)
        )
    }

    fun visitSigma(context: VcSigmaExpr): Concrete.SigmaExpression {
        val args = visitTeles(context.teleList)
        args.forEach {
            if (!it.explicit) {
                reportError(it.position, "Fields in sigma types must be explicit")
            }
        }
        return Concrete.SigmaExpression(elementPosition(context), args)
    }

    fun visitLam(context: VcLamExpr): Concrete.Expression {
        return Concrete.LamExpression(
                elementPosition(context),
                visitLamTeles(context.teleList),
                visitExpr(context.expr)
        )
    }

    fun visitLet(context: VcLetExpr): Concrete.LetExpression {
        val clauses = context.letClauseList.map { visitLetClause(it) }
        return Concrete.LetExpression(elementPosition(context), clauses, visitExpr(context.expr))
    }

    fun visitCase(context: VcCaseExpr): Concrete.Expression {
        val elimExprs = context.expr0List.map { visitExpr0(it) }
        val clauses = context.clauseList.map { visitClause(it) }
        return Concrete.CaseExpression(elementPosition(context), elimExprs, clauses)
    }

    fun visitClauses(context: VcClauses): List<Concrete.FunctionClause> =
            context.clauseList.map { visitClause(it) }

    fun visitLetClause(context: VcLetClause): Concrete.LetClause {
        val name = context.id.text
        val arguments = visitLamTeles(context.teleList)
        val resultType = context.typeAnnotation?.let { visitExpr(it.expr) }
        return Concrete.LetClause(
                elementPosition(context),
                name,
                arguments,
                resultType,
                visitExpr(context.expr)
        )
    }

    fun visitClause(context: VcClause): Concrete.FunctionClause {
        val patterns = context.patternList.map { visitPattern(it) }
        val expression = context.expr?.let { visitExpr(it) }
        return Concrete.FunctionClause(elementPosition(context), patterns, expression)
    }

    fun visitLevelExpr(context: VcLevelExpr): Concrete.LevelExpression? {
        context.atomLevelExpr?.let { return visitAtomLevelExpr(it) }
        context.maxLevelExpr?.let { return visitMaxLevelExpr(it) }
        context.sucLevelExpr?.let { return visitSucLevelExpr(it) }
        throw IllegalStateException()
    }

    fun visitAtomLevelExpr(context: VcAtomLevelExpr?): Concrete.LevelExpression? {
        context?.lpKw?.let { return Concrete.PLevelExpression(elementPosition(context)) }
        context?.lhKw?.let { return Concrete.HLevelExpression(elementPosition(context)) }
        context?.number?.let {
            val number = Integer.parseInt(it.text)
            return Concrete.NumberLevelExpression(elementPosition(it), number)
        }
        context?.levelExpr?.let { return visitLevelExpr(it) }
        throw IllegalStateException()
    }

    fun visitSucLevelExpr(context: VcSucLevelExpr): Concrete.SucLevelExpression {
        return Concrete.SucLevelExpression(
                elementPosition(context),
                visitAtomLevelExpr(context.atomLevelExpr)
        )
    }

    fun visitMaxLevelExpr(context: VcMaxLevelExpr): Concrete.MaxLevelExpression {
        return Concrete.MaxLevelExpression(
                elementPosition(context),
                visitAtomLevelExpr(context.atomLevelExprList[0]),
                visitAtomLevelExpr(context.atomLevelExprList[1])
        )
    }

    fun visitBinOpArg(arg: VcBinOpArg?): Concrete.Expression {
        arg?.argumentBinOp?.let { return visitBinOpArgument(it) }
        arg?.universeBinOp?.let { return visitUniverse(it) }
        arg?.setUniverseBinOp?.let { return visitSetUniverse(it) }
        arg?.truncatedUniverseBinOp?.let { return visitTruncatedUniverse(it) }
        throw IllegalStateException()
    }

    fun visitBinOpArgument(context: VcArgumentBinOp): Concrete.Expression =
            visitArguments(visitAtomFieldsAcc(context.atomFieldsAcc), context.argumentList)

    fun visitUniverse(context: VcUniverseBinOp): Concrete.UniverseExpression {
        val text = context.universe.text.substring("\\Type".length)

        var lp: Concrete.LevelExpression? = null
        if (text.isNotEmpty()) {
            val position = elementPosition(context.universe)
            val number = Integer.parseInt(text)
            lp = Concrete.NumberLevelExpression(position, number)
        }

        var lh: Concrete.LevelExpression? = null
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

        return Concrete.UniverseExpression(elementPosition(context), lp, lh)
    }

    fun visitSetUniverse(context: VcSetUniverseBinOp): Concrete.UniverseExpression {
        val text = context.set.text.substring("\\Set".length)
        val pLevel: Concrete.LevelExpression? = if (text.isEmpty()) {
            context.atomLevelExpr?.let { visitAtomLevelExpr(it) }
        } else {
            if (context.atomLevelExpr != null) {
                reportError(elementPosition(context.atomLevelExpr), "p-level is already specified")
            }
            val number = Integer.parseInt(text)
            Concrete.NumberLevelExpression(elementPosition(context.set), number)
        }
        val position = elementPosition(context)
        val numberLevel = Concrete.NumberLevelExpression(position, 0)
        return Concrete.UniverseExpression(position, pLevel, numberLevel)
    }

    fun visitTruncatedUniverse(context: VcTruncatedUniverseBinOp): Concrete.UniverseExpression {
        val text = context.truncatedUniverse.text.let {
            it.substring(it.indexOf('-') + "-Type".length)
        }
        val pLevel: Concrete.LevelExpression? = if (text.isEmpty()) {
            context.atomLevelExpr?.let { visitAtomLevelExpr(it) }
        } else {
            if (context.atomLevelExpr != null) {
                reportError(elementPosition(context.atomLevelExpr), "p-level is already specified")
            }
            val number = Integer.parseInt(text)
            Concrete.NumberLevelExpression(elementPosition(context.truncatedUniverse), number)
        }
        val truncatedUniverse = parseTruncatedUniverse(context.truncatedUniverse)
        return Concrete.UniverseExpression(elementPosition(context), pLevel, truncatedUniverse)
    }

    private fun parseBinOpSequence(
            context: List<VcBinOpLeft>,
            expression: Concrete.Expression,
            position: Concrete.Position
    ): Concrete.Expression {
        var left: Concrete.Expression? = null
        var binOp: Concrete.ReferenceExpression? = null
        val sequence = mutableListOf<Abstract.BinOpSequenceElem>()
        for (leftContext in context) {
            val newExpr = leftContext.newExpr
            val expr = parseImplementations(
                    newExpr.newKw != null,
                    newExpr.implementStatements,
                    position,
                    visitBinOpArg(newExpr.binOpArg)
            )

            if (left == null) {
                left = expr
            } else {
                sequence.add(Abstract.BinOpSequenceElem(binOp, expr))
            }

            val name = visitInfix(leftContext.infix)
            binOp = Concrete.ReferenceExpression(elementPosition(leftContext.infix), null, name)
        }
        left ?: return expression
        sequence.add(Abstract.BinOpSequenceElem(binOp, expression))
        return Concrete.BinOpSequenceExpression(position, left, sequence)
    }

    fun visitInfix(context: VcInfix): String {
        context.binOp?.let { return it.text }
        context.id?.let { return it.text }
        throw IllegalStateException()
    }

    fun visitModulePath(context: VcModulePath): List<String> = context.pathPartList.map { it.text }

    fun visitAtom(expr: VcAtom): Concrete.Expression {
        expr.atomModuleCall?.let { return visitAtomModuleCall(it) }
        expr.literal?.let { return visitLiteral(it) }
        expr.tuple?.let { return visitTuple(it) }
        expr.number?.let { return visitAtomNumber(it) }
        throw IllegalStateException()
    }

    fun visitAtomModuleCall(context: VcAtomModuleCall): Concrete.ModuleCallExpression {
        return Concrete.ModuleCallExpression(
                elementPosition(context),
                visitModulePath(context.modulePath)
        )
    }

    fun visitTuple(context: VcTuple): Concrete.Expression {
        return if (context.exprList.size == 1) {
            visitExpr(context.exprList.first())
        } else {
            val fields = context.exprList.map { visitExpr(it) }
            Concrete.TupleExpression(elementPosition(context), fields)
        }
    }

    fun visitAtomNumber(context: PsiElement): Concrete.NumericLiteral {
        val number = Integer.parseInt(context.text)
        return Concrete.NumericLiteral(elementPosition(context), number)
    }

    fun visitAtomFieldsAcc(context: VcAtomFieldsAcc?): Concrete.Expression {
        context ?: throw IllegalStateException()
        var expr = visitAtom(context.atom)
        for (acc in context.fieldAccList) {
            expr = if (acc.identifier != null) {
                Concrete.ReferenceExpression(
                        elementPosition(acc),
                        expr,
                        visitName(acc.identifier)
                )
            } else if (acc.number != null) {
                val field = Integer.parseInt(acc.number?.text) - 1
                Concrete.ProjExpression(
                        elementPosition(acc),
                        expr,
                        field
                )
            } else {
                throw IllegalStateException()
            }
        }
        return expr
    }

    private fun parseImplementations(
            withNewContext: Boolean,
            context: VcImplementStatements?,
            position: Concrete.Position,
            expr: Concrete.Expression
    ): Concrete.Expression {
        var implementations = expr

        if (context != null) {
            val implementStatements = context.implementStatementList.map {
                Concrete.ClassFieldImpl(
                        elementPosition(it.identifier),
                        visitName(it.identifier),
                        visitExpr(it.expr)
                )
            }
            implementations = Concrete.ClassExtExpression(
                    position,
                    implementations,
                    implementStatements
            )
        }

        if (withNewContext) {
            implementations = Concrete.NewExpression(position, implementations)
        }

        return implementations
    }

    private fun visitArguments(
            expr: Concrete.Expression,
            arguments: List<VcArgument>
    ): Concrete.Expression {
        var appExpr = expr
        for (arg in arguments) {
            val expr1 = when {
                arg.atomFieldsAcc != null -> visitAtomFieldsAcc(arg.atomFieldsAcc)
                arg.expr != null -> visitExpr(arg.expr)
                arg.universeAtom != null -> visitUniverseAtom(arg.universeAtom)
                else -> throw IllegalStateException()
            }
            val argumentExpr = Concrete.ArgumentExpression(expr1, arg.expr == null, false)
            appExpr = Concrete.AppExpression(expr.position, appExpr, argumentExpr)
        }
        return appExpr
    }

    fun visitLiteral(expr: VcLiteral): Concrete.Expression {
        expr.identifier?.let {
            return Concrete.ReferenceExpression(elementPosition(expr), null, visitName(it))
        }
        expr.propKw?.let {
            val position = elementPosition(expr)
            return Concrete.UniverseExpression(
                    position,
                    Concrete.NumberLevelExpression(position, 0),
                    Concrete.NumberLevelExpression(position, -1)
            )
        }
        expr.underscore?.let { return Concrete.InferHoleExpression(elementPosition(expr)) }
        expr.hole?.let { return Concrete.ErrorExpression(elementPosition(expr)) }
        throw IllegalStateException()
    }

    fun visitUniTruncatedUniverse(context: VcUniverseAtom): Concrete.UniverseExpression {
        val truncatedUniverse = context.truncatedUniverse ?: throw IllegalStateException()
        val text = truncatedUniverse.text.let { it.substring(it.indexOf('-') + "-Type".length) }
        val pLevel = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Concrete.NumberLevelExpression(elementPosition(truncatedUniverse), number)
        } else {
            null
        }
        return Concrete.UniverseExpression(
                elementPosition(context),
                pLevel,
                parseTruncatedUniverse(truncatedUniverse)
        )
    }

    private fun parseTruncatedUniverse(terminal: PsiElement): Concrete.LevelExpression {
        val universe = terminal.text
        if (universe[1] == 'o') {
            return Concrete.InfLevelExpression(elementPosition(terminal))
        }
        val number = Integer.parseInt(universe.substring(1, universe.indexOf('-')))
        return Concrete.NumberLevelExpression(elementPosition(terminal), number)
    }

    fun visitUniverseAtom(context: VcUniverseAtom?): Concrete.UniverseExpression {
        context?.set?.let { return visitUniSetUniverse(context) }
        context?.truncatedUniverse?.let { return visitUniTruncatedUniverse(context) }
        context?.universe?.let { return visitUniUniverse(context) }
        throw IllegalStateException()
    }

    fun visitUniUniverse(context: VcUniverseAtom): Concrete.UniverseExpression {
        val universe = context.universe ?: throw IllegalStateException()
        val text = universe.text.substring("\\Type".length)
        val lp = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Concrete.NumberLevelExpression(elementPosition(universe), number)
        } else {
            null
        }
        return Concrete.UniverseExpression(elementPosition(context), lp, null)
    }

    fun visitUniSetUniverse(context: VcUniverseAtom): Concrete.UniverseExpression {
        val text = context.set?.text?.substring("\\Set".length) ?: throw IllegalStateException()
        val pLevel = if (text.isNotEmpty()) {
            val number = Integer.parseInt(text)
            Concrete.NumberLevelExpression(elementPosition(context.set), number)
        } else {
            null
        }
        val position = elementPosition(context)
        return Concrete.UniverseExpression(
                position,
                pLevel,
                Concrete.NumberLevelExpression(position, 0)
        )
    }

    private fun visitLamTele(tele: VcTele): List<Concrete.Argument>? {
        val arguments = ArrayList<Concrete.Argument>(3)
        if (tele.typedExpr != null) {
            val explicit = tele.lparen != null
            val typedExpr = tele.typedExpr ?: return null
            val varsExpr1 = typedExpr.unknownOrIDList
            val varsExpr2 = typedExpr.expr

            val typeExpr = if (typedExpr.colon != null) {
                visitExpr(typedExpr.expr)
            } else {
                null
            }

            val vars = if (typedExpr.colon != null) {
                varsExpr1.map { getVar(it) }
            } else {
                getVars(varsExpr2)
            }

            if (typeExpr == null) {
                if (explicit) {
                    arguments.addAll(vars.requireNoNulls())
                } else {
                    vars.mapTo(arguments) {
                        Concrete.NameArgument(it?.position, false, it?.referable)
                    }
                }
            } else {
                val arg = Concrete.TelescopeArgument(
                        elementPosition(tele),
                        explicit,
                        vars.map { it?.referable },
                        typeExpr
                )
                arguments.add(arg)
            }
        } else {
            var ok = tele.literal != null
            if (ok) {
                val literalContext = tele.literal
                if (literalContext?.identifier?.id != null) {
                    val id = literalContext.identifier?.id
                    val position = elementPosition(id)
                    val variable = Concrete.LocalVariable(position, id?.text)
                    arguments.add(Concrete.NameArgument(position, true, variable))
                } else if (literalContext?.underscore != null) {
                    val arg = Concrete.NameArgument(elementPosition(literalContext), true, null)
                    arguments.add(arg)
                } else {
                    ok = false
                }
            }
            if (!ok) {
                reportError(elementPosition(tele), "Unexpected token, expected an identifier")
                throw ParseException()
            }
        }
        return arguments
    }

    private fun visitLamTeles(tele: List<VcTele>): List<Concrete.Argument> =
            tele.map { visitLamTele(it) }.filterNotNull().flatten()

    private fun visitTeles(teles: List<VcTele>): List<Concrete.TypeArgument> {
        val arguments = mutableListOf<Concrete.TypeArgument>()
        for (tele in teles) {
            val explicit = tele.lbrace == null
            var typedExpr: VcTypedExpr?
            if (explicit) {
                if (tele.lparen != null) {
                    typedExpr = tele.typedExpr
                } else if (tele.literal != null) {
                    val literal = tele.literal?.let { visitLiteral(it) }
                    arguments.add(Concrete.TypeArgument(true, literal))
                    continue
                } else if (tele.universeAtom != null) {
                    val universeAtom = tele.universeAtom?.let { visitUniverseAtom(it) }
                    arguments.add(Concrete.TypeArgument(true, universeAtom))
                    continue
                } else {
                    throw IllegalStateException()
                }
            } else {
                typedExpr = tele.typedExpr
            }

            if (typedExpr?.colon != null) {
                val args = typedExpr.unknownOrIDList.map { getVar(it) }
                val vars = args.map { it?.referable }
                val arg = Concrete.TelescopeArgument(
                        elementPosition(tele),
                        explicit,
                        vars,
                        visitExpr(typedExpr.expr)
                )
                arguments.add(arg)
            } else {
                val arg = Concrete.TypeArgument(explicit, visitExpr(typedExpr?.expr))
                arguments.add(arg)
            }
        }
        return arguments
    }

    private fun visitFunctionArguments(teleContext: List<VcTele>): List<Concrete.Argument> {
        val arguments = mutableListOf<Concrete.Argument>()
        for (tele in teleContext) {
            val args = visitLamTele(tele)
            if (args != null && args.isNotEmpty()) {
                if (args.first() is Concrete.TelescopeArgument) {
                    arguments.add(args.first())
                } else {
                    reportError(elementPosition(tele), "Expected a typed variable")
                }
            }
        }
        return arguments
    }

    // Utils

    private fun elementPosition(token: PsiElement?): Concrete.Position =
            Concrete.Position(module, 0, 0)

    private fun getVar(context: VcAtomFieldsAcc): Concrete.NameArgument? =
            if (context.fieldAccList.isEmpty()) getVar(context.atom.literal) else null

    private fun getVar(literal: VcLiteral?): Concrete.NameArgument? {
        return (literal?.identifier?.id ?: literal?.underscore)?.let {
            val position = elementPosition(it)
            val variable = Concrete.LocalVariable(position, it.text)
            Concrete.NameArgument(position, true, variable)
        }
    }

    private fun getVar(maybeVar: VcUnknownOrID): Concrete.NameArgument? {
        (maybeVar.identifier?.id ?: maybeVar.underscore)?.let {
            val position = elementPosition(it)
            val variable = Concrete.LocalVariable(position, it.text)
            return Concrete.NameArgument(position, true, variable)
        }
        throw IllegalStateException()
    }

    private fun getVarsNull(expr: VcExpr): List<Concrete.NameArgument>? {
        if (expr !is VcBinOpExpr
                || expr.binOpLeftList.isNotEmpty()
                || expr.newExpr.binOpArg?.argumentBinOp == null
                || expr.newExpr.newKw != null
                || expr.newExpr.implementStatements != null
                ) {
            return null
        }

        val argumentBinOp = expr.newExpr.binOpArg?.argumentBinOp ?: return null
        val firstArg = argumentBinOp.atomFieldsAcc.let { getVar(it) } ?: return null
        val result = mutableListOf(firstArg)
        for (argument in argumentBinOp.argumentList) {
            if (argument.atomFieldsAcc != null) {
                val arg = argument.atomFieldsAcc?.let { getVar(it) } ?: return null
                result.add(arg)
            } else if (argument.expr != null) {
                val arguments = argument.expr?.let { getVarsNull(it) } ?: return null
                arguments.forEach { it.explicit = false }
                result.addAll(arguments)
            } else {
                return null
            }
        }
        return result
    }

    private fun getVars(expr: VcExpr): List<Concrete.NameArgument> {
        val result = getVarsNull(expr)
        if (result != null) {
            return result
        } else {
            reportError(elementPosition(expr), "Expected a list of variables")
            throw ParseException()
        }
    }

    // Errors

    val MISPLACES_DEFINITION = "This definition is not allowed here"

    private fun reportError(position: Concrete.Position, message: String) {
        val error = ParserError(position, message)
        errorReporter.report(error)
    }
}
