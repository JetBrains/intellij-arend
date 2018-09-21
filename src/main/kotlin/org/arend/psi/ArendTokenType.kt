package org.arend.psi

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.arend.ArendLanguage
import org.arend.psi.ArendElementTypes.*

class ArendTokenType(debugName: String) : IElementType(debugName, ArendLanguage.INSTANCE)

val AREND_KEYWORDS: TokenSet = TokenSet.create(
        OPEN_KW, IMPORT_KW, USING_KW, AS_KW, HIDING_KW, FUNCTION_KW, NON_ASSOC_KW,
        LEFT_ASSOC_KW, RIGHT_ASSOC_KW, INFIX_NON_KW, INFIX_LEFT_KW, INFIX_RIGHT_KW,
        PROP_KW, WHERE_KW, WITH_KW, COWITH_KW, ELIM_KW, NEW_KW, PI_KW, SIGMA_KW, LAM_KW, LET_KW,
        IN_KW, CASE_KW, WITH_KW, DATA_KW, CLASS_KW, RECORD_KW, MODULE_KW, EXTENDS_KW,
        RETURN_KW, COERCE_KW, INSTANCE_KW, TRUNCATED_KW,
        LP_KW, LH_KW, SUC_KW, MAX_KW, LEVELS_KW,
        SET, UNIVERSE, TRUNCATED_UNIVERSE
)

val AREND_COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)

val AREND_NAMES: TokenSet = TokenSet.create(ID, INFIX, POSTFIX)

val AREND_WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
