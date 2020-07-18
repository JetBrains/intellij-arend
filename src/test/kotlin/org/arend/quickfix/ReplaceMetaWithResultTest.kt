package org.arend.quickfix

import org.arend.ext.concrete.expr.ConcreteArgument
import org.arend.ext.concrete.expr.ConcreteReferenceExpression
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.ExpressionResolver
import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.MetaDefinition
import org.arend.ext.typechecking.MetaResolver

class ReplaceMetaWithResultTest : QuickFixTestBase() {
    fun `test meta definition`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, object : MetaDefinition {
                override fun getConcreteRepresentation(arguments: List<ConcreteArgument>) = arguments[1].expression
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
                override fun resolvePrefix(resolver: ExpressionResolver, refExpr: ConcreteReferenceExpression, arguments: List<ConcreteArgument>) = arguments[1].expression
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
}