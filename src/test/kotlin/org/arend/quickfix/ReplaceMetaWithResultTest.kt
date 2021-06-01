package org.arend.quickfix

import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.ExpressionResolver
import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.*
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle

class ReplaceMetaWithResultTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.expression.replaceMetaWithResult")

    private fun addMeta() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, object : MetaDefinition {
                override fun invokeMeta(typechecker: ExpressionTypechecker, contextData: ContextData) =
                    typechecker.typecheck(contextData.arguments[1].expression, null)
            })
        }
    }

    fun `test meta definition`() {
        addMeta()
        simpleQuickFixTest(fixName, """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3
        """, """
            \import Meta
            \func test => 2
        """)
    }

    fun `test meta in binop`() {
        addMeta()
        simpleQuickFixTest(fixName, """
            \import Meta
            \func test (n : Nat) => 1 Nat.+ {-caret-}myMeta 1 n 3 Nat.+ 2
        """, """
            \import Meta
            \func test (n : Nat) => 1 Nat.+ n Nat.+ 2
        """)
    }

    fun `test meta resolver`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, contextData: ContextData) =
                    Concrete.TupleExpression(contextData.marker.data, contextData.arguments.map { it.expression as Concrete.Expression })
            })
        }

        simpleQuickFixTest(fixName, """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3
        """, """
            \import Meta
            \func test => (1, 2, 3)
        """)
    }

    fun addResolver() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, contextData: ContextData) =
                    resolver.resolve(contextData.arguments[1].expression)
            })
        }
    }

    fun `test meta resolver argument`() {
        addResolver()
        simpleQuickFixTest(fixName, """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3
        """, """
            \import Meta
            \func test => 2
        """)
    }

    fun `test meta resolver argument in binop`() {
        addResolver()
        simpleQuickFixTest(fixName, """
            \import Meta
            \func test (n : Nat) => 1 Nat.+ {-caret-}myMeta 1 n 3 Nat.+ 2
        """, """
            \import Meta
            \func test (n : Nat) => 1 Nat.+ n Nat.+ 2
        """)
    }

    fun `test meta resolver with clauses`() {
        addResolver()
        simpleQuickFixTest(fixName, """
            \import Meta
            \func test => {-caret-}myMeta 1 2 3 \with {}
        """, """
            \import Meta
            \func test => 2
        """)
    }

    fun `test meta resolver with case`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, contextData: ContextData): ConcreteExpression {
                    val factory = ConcreteFactoryImpl(contextData.marker.data)
                    return resolver.resolve(factory.caseExpr(false, listOf(factory.caseArg(contextData.arguments[0].expression, null, null)), null, null, contextData.clauses?.clauseList ?: emptyList()))
                }
            })
        }

        simpleQuickFixTest(fixName, """
            \import Meta
            \func test (n : Nat) : Nat => {-caret-}myMeta n \with { | 0 => 0 | suc n => n }
        """, """
            \import Meta
            \func test (n : Nat) : Nat => \case n \with {
              | 0 => 0
              | suc n => n
            }
        """)
    }
}