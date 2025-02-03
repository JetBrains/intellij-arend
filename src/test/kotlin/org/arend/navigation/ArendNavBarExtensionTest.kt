package org.arend.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.impl.pathToItem
import org.arend.ArendTestBase

class ArendNavBarExtensionTest : ArendTestBase() {

    private fun contextNavBarPathStrings(ctx: DataContext): List<String> {
        val contextItem = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
            ?.dereference()
            ?: return emptyList()
        return contextItem.pathToItem().map {
            (it.presentation() as NavBarItemPresentationData).text
        }
    }

    private fun doTest(text: String, expectedItems: List<String>) {
        InlineFile(text).withCaret()
        val actualItems = contextNavBarPathStrings((myFixture.editor as EditorEx).dataContext)
        assertOrderedEquals(actualItems, expectedItems)
    }

    fun testFunction() {
        doTest("""
            \func f{-caret-} => {?}
        """.trimIndent(), listOf("src", "Main", "f"))
    }

    fun testClass() {
        doTest("""
            \class {-caret-}C
        """.trimIndent(), listOf("src", "Main", "C"))
    }

    fun testClassWithField() {
        doTest("""
            \class C (n{-caret-}at : Nat)
        """.trimIndent(), listOf("src", "Main", "C", "nat"))
    }

    fun testClassWithClassField() {
        doTest("""
            \class C {
                | nat : Na{-caret-}t
            }
        """.trimIndent(), listOf("src", "Main", "C", "nat"))
    }

    fun testClassWithOverrideField() {
        doTest("""
            \class O (nat : Int)
            
            \class C \extends O {
                \override nat : Na{-caret-}t
            }
        """.trimIndent(), listOf("src", "Main", "C", "nat"))
    }

    fun testFuncUsingCoWith() {
        doTest("""
            \class C (nat : Nat)

            \func f : C \cowith {
              | nat {-caret-}=> {?}
            }
        """.trimIndent(), listOf("src", "Main", "f", "nat"))
    }
}
