package org.arend.quickfix

import org.arend.ext.concrete.expr.ConcreteArgument
import org.arend.ext.concrete.expr.ConcreteReferenceExpression
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.ExpressionResolver
import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.*
import org.arend.term.concrete.Concrete

class ReplaceMetaWithResultTest : QuickFixTestBase() {
    fun `test meta definition`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, object : MetaDefinition {
                override fun invokeMeta(typechecker: ExpressionTypechecker, contextData: ContextData) =
                    typechecker.typecheck(contextData.arguments[1].expression, null)
            })
        }

        simpleQuickFixTest("Replace meta with result", """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3
        """, """
            \import Meta
            \func test => 2
        """)
    }

    fun `test meta resolver`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, refExpr: ConcreteReferenceExpression, arguments: List<ConcreteArgument>) =
                    Concrete.TupleExpression(refExpr.data, arguments.map { it.expression as Concrete.Expression })
            })
        }

        simpleQuickFixTest("Replace meta with result", """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3
        """, """
            \import Meta
            \func test => (1, 2, 3)
        """)
    }
}