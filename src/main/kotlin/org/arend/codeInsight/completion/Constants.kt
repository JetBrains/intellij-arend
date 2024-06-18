package org.arend.codeInsight.completion

import org.arend.psi.ArendElementTypes.*


val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW).map { it.toString() }
val STATEMENT_WT_KWS_TOKENS = listOf(FUNC_KW, SFUNC_KW, LEMMA_KW, TYPE_KW, CONS_KW, DATA_KW, CLASS_KW, RECORD_KW, INSTANCE_KW, OPEN_KW, MODULE_KW, META_KW, PLEVELS_KW, HLEVELS_KW, AXIOM_KW)
val STATEMENT_WT_KWS = STATEMENT_WT_KWS_TOKENS.map { it.toString() }
val ACCESS_MODIFIERS = listOf(PROTECTED_KW, PRIVATE_KW).map { it.toString() }
val CLASS_MEMBER_KWS = listOf(FIELD_KW, PROPERTY_KW, OVERRIDE_KW, DEFAULT_KW).map { it.toString() }
val SIGMA_TELE_START_KWS = listOf(PROPERTY_KW).map { it.toString() }
val DATA_UNIVERSE_KW = listOf("\\Type", "\\Set", PROP_KW.toString(), "\\oo-Type", "\\hType")
val BASIC_EXPRESSION_KW = listOf(PI_KW, SIGMA_KW, LAM_KW, HAVE_KW, HAVES_KW, LET_KW, LETS_KW, CASE_KW, SCASE_KW).map { it.toString() }
val LEVEL_KWS = listOf(MAX_KW, SUC_KW).map { it.toString() }
val LPH_KW_LIST = listOf(LP_KW, LH_KW, OO_KW).map { it.toString() }
val PH_LEVELS_KW_LIST = listOf(PLEVELS_KW, HLEVELS_KW).map { it.toString() }
val ELIM_WITH_KW_LIST = listOf(ELIM_KW, WITH_KW).map { it.toString() }
val COERCE_LEVEL_KWS = listOf(COERCE_KW, LEVEL_KW).map { it.toString() }
val NEW_KW_LIST = listOf(NEW_KW.toString(), EVAL_KW.toString(), PEVAL_KW.toString(), BOX_KW.toString())
val TRUNCATED_DATA_KW = "$TRUNCATED_KW $DATA_KW"

val AS_KW_LIST = listOf(AS_KW.toString())
val USING_KW_LIST = listOf(USING_KW.toString())
val HIDING_KW_LIST = listOf(HIDING_KW.toString())
val EXTENDS_KW_LIST = listOf(EXTENDS_KW.toString())
val DATA_KW_LIST = listOf(DATA_KW.toString())
val IMPORT_KW_LIST = listOf(IMPORT_KW.toString())
val WHERE_KW_LIST = listOf(WHERE_KW.toString())
val WHERE_KW_FULL = WHERE_KW_LIST.map { "$it {}" }
val FAKE_NTYPE_LIST = listOf("\\n-Type")
val IN_KW_LIST = listOf(IN_KW.toString())
val WITH_KW_LIST = listOf(WITH_KW.toString())
val WITH_KW_FULL = WITH_KW_LIST.map { "$it {}" }
val LEVEL_KW_LIST = listOf(LEVEL_KW.toString())
val COERCE_KW_LIST = listOf(COERCE_KW.toString())
val USE_KW_LIST = listOf(USE_KW.toString())
val RETURN_KW_LIST = listOf(RETURN_KW.toString())
val COWITH_KW_LIST = listOf(COWITH_KW.toString())
val ELIM_KW_LIST = listOf(ELIM_KW.toString())
val CLASSIFYING_KW_LIST = listOf(CLASSIFYING_KW.toString())
val NO_CLASSIFYING_KW_LIST = listOf(NO_CLASSIFYING_KW.toString())
val TRUNCATED_DATA_KW_LIST = listOf(TRUNCATED_DATA_KW)
val TRUNCATED_KW_LIST = listOf(TRUNCATED_KW.toString())
val ALIAS_KW_LIST = listOf(ALIAS_KW.toString())
val LEVELS_KW_LIST = listOf(LEVELS_KW.toString())
val PARAM_ATTR_LIST = listOf(STRICT_KW.toString(), PROPERTY_KW.toString())
val HLEVELS_KW_LIST = listOf(HLEVELS_KW.toString())
val THIS_KW_LIST = listOf(THIS_KW.toString())

val LOCAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_DATA_KW_LIST
val GLOBAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_DATA_KW_LIST + IMPORT_KW_LIST + ACCESS_MODIFIERS
val HU_KW_LIST = USING_KW_LIST + HIDING_KW_LIST
val DATA_OR_EXPRESSION_KW = DATA_UNIVERSE_KW + BASIC_EXPRESSION_KW + NEW_KW_LIST
val LPH_LEVEL_KWS = LPH_KW_LIST + LEVEL_KWS
