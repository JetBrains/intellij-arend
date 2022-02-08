package org.arend.search.proof

import junit.framework.TestCase
import org.arend.ArendTestBase

class ParsingTest : ArendTestBase() {
    private fun doTest(pattern: String, serialized: String) {
        val query = ProofSearchQuery.fromString(pattern)
        TestCase.assertTrue("Expected correct parsing", query is ParsingResult.OK)
        val result = (query as ParsingResult.OK).value
        TestCase.assertTrue("Expected: $serialized, got: $result", result.toString() == serialized)
    }

    private fun doTestFail(pattern: String) {
        val query = ProofSearchQuery.fromString(pattern)
        TestCase.assertTrue("Expected failure", query is ParsingResult.Error)
    }

    fun test1() = doTest("a -> b", "<a> --> <b>")
    fun test2() = doTest("a", "<a>")
    fun test3() = doTest("a \\and b", "<a  \\and  b>")
    fun test4() = doTestFail("a ->")
    fun test5() = doTestFail("-> a")
    fun test6() = doTestFail("a \\and")
    fun test7() = doTestFail("\\and a")
    fun test8() = doTest("a b", "<[a b]>")
    fun test9() = doTest("a \\and b -> c \\and d -> e \\and f", "<a  \\and  b> -> <c  \\and  d> --> <e  \\and  f>")
    fun test10() = doTestFail("")
    fun test11() = doTestFail("()")
    fun test12() = doTest("(a)", "<a>")
    fun test13() = doTestFail("a \\and ->")
    fun test14() = doTestFail("(a")
    fun test15() = doTestFail("a)")
    fun test16() = doTestFail("((a)")
    fun test17() = doTestFail("{)")
    fun test18() = doTestFail("-> ->")
    fun test19() = doTest("_ = _ \\and _ -> _ = (_ = _)", "<[_ = _]  \\and  _> --> <[_ = [_ = _]]>")
}