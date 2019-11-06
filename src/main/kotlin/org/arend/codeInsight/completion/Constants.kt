package org.arend.codeInsight.completion

import org.arend.psi.ArendElementTypes.*


val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW).map { it.toString() }
val STATEMENT_WT_KWS = listOf(FUNC_KW, SFUNC_KW, LEMMA_KW, CONS_KW, DATA_KW, CLASS_KW, RECORD_KW, INSTANCE_KW, OPEN_KW, MODULE_KW).map { it.toString() }
val CLASS_MEMBER_KWS = listOf(FIELD_KW, PROPERTY_KW).map { it.toString() }
val DATA_UNIVERSE_KW = listOf("\\Type", "\\Set", PROP_KW.toString(), "\\oo-Type")
val BASIC_EXPRESSION_KW = listOf(PI_KW, SIGMA_KW, LAM_KW, LET_KW, LETS_KW, CASE_KW, SCASE_KW).map { it.toString() }
val LEVEL_KWS = listOf(MAX_KW, SUC_KW).map { it.toString() }
val LPH_KW_LIST = listOf(LP_KW, LH_KW, OO_KW).map { it.toString() }
val ELIM_WITH_KW_LIST = listOf(ELIM_KW, WITH_KW).map { it.toString() }
val COERCE_LEVEL_KWS = listOf(COERCE_KW, LEVEL_KW).map { it.toString() }

val AS_KW_LIST = listOf(AS_KW.toString())
val USING_KW_LIST = listOf(USING_KW.toString())
val HIDING_KW_LIST = listOf(HIDING_KW.toString())
val EXTENDS_KW_LIST = listOf(EXTENDS_KW.toString())
val DATA_KW_LIST = listOf(DATA_KW.toString())
val IMPORT_KW_LIST = listOf(IMPORT_KW.toString())
val WHERE_KW_LIST = listOf(WHERE_KW.toString())
val TRUNCATED_KW_LIST = listOf(TRUNCATED_KW.toString())
val NEW_KW_LIST = listOf(NEW_KW.toString(), EVAL_KW.toString(), PEVAL_KW.toString())
val FAKE_NTYPE_LIST = listOf("\\n-Type")
val IN_KW_LIST = listOf(IN_KW.toString())
val WITH_KW_LIST = listOf(WITH_KW.toString())
val LEVEL_KW_LIST = listOf(LEVEL_KW.toString())
val COERCE_KW_LIST = listOf(COERCE_KW.toString())
val PROP_KW_LIST = listOf(PROP_KW.toString())
val USE_KW_LIST = listOf(USE_KW.toString())
val RETURN_KW_LIST = listOf(RETURN_KW.toString())
val COWITH_KW_LIST = listOf(COWITH_KW.toString())
val ELIM_KW_LIST = listOf(ELIM_KW.toString())
val CLASSIFYING_KW_LIST = listOf(CLASSIFYING_KW.toString())

val LOCAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST
val GLOBAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST + IMPORT_KW_LIST
val CLASS_STATEMENT_KWS = LOCAL_STATEMENT_KWS + CLASS_MEMBER_KWS
val ALL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST + IMPORT_KW_LIST + USE_KW_LIST + CLASS_MEMBER_KWS
val HU_KW_LIST = USING_KW_LIST + HIDING_KW_LIST
val DATA_OR_EXPRESSION_KW = DATA_UNIVERSE_KW + BASIC_EXPRESSION_KW + NEW_KW_LIST
val LPH_LEVEL_KWS = LPH_KW_LIST + LEVEL_KWS
