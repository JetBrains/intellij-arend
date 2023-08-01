package org.arend.quickfix

import org.arend.util.ArendBundle

class LambdaInferenceQuickFixTest : QuickFixTestBase() {

    fun testLambdaInference1() = typedQuickFixTest(
        ArendBundle.message("arend.argument.inference.parameter"), """
        \func f => \lam x{-caret-} {y} => x
    """, """
        \func f => \lam (x : {?}) {y} => x
    """
    )

    fun testLambdaInference2() = typedQuickFixTest(
        ArendBundle.message("arend.argument.inference.parameter"), """
        \func f => \lam (x : {?}) {y{-caret-}} => x
    """, """
        \func f => \lam (x : {?}) {y : {?}} => x
    """
    )
}
