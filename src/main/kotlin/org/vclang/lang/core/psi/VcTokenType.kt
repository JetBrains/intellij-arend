package org.vclang.lang.core.psi

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.psi.VcElementTypes.BY_KW
import org.vclang.lang.core.psi.VcElementTypes.CASE_KW
import org.vclang.lang.core.psi.VcElementTypes.CLASS_KW
import org.vclang.lang.core.psi.VcElementTypes.DATA_KW
import org.vclang.lang.core.psi.VcElementTypes.DEFAULT_KW
import org.vclang.lang.core.psi.VcElementTypes.ELIM_KW
import org.vclang.lang.core.psi.VcElementTypes.EXPORT_KW
import org.vclang.lang.core.psi.VcElementTypes.EXTENDS_KW
import org.vclang.lang.core.psi.VcElementTypes.FUNCTION_KW
import org.vclang.lang.core.psi.VcElementTypes.HIDING_KW
import org.vclang.lang.core.psi.VcElementTypes.INSTANCE_KW
import org.vclang.lang.core.psi.VcElementTypes.IN_KW
import org.vclang.lang.core.psi.VcElementTypes.LAM_KW
import org.vclang.lang.core.psi.VcElementTypes.LEFT_ASSOC_KW
import org.vclang.lang.core.psi.VcElementTypes.LET_KW
import org.vclang.lang.core.psi.VcElementTypes.LH_KW
import org.vclang.lang.core.psi.VcElementTypes.LP_KW
import org.vclang.lang.core.psi.VcElementTypes.MAX_KW
import org.vclang.lang.core.psi.VcElementTypes.NEW_KW
import org.vclang.lang.core.psi.VcElementTypes.NON_ASSOC_KW
import org.vclang.lang.core.psi.VcElementTypes.ON_KW
import org.vclang.lang.core.psi.VcElementTypes.OPEN_KW
import org.vclang.lang.core.psi.VcElementTypes.PI_KW
import org.vclang.lang.core.psi.VcElementTypes.PROP_KW
import org.vclang.lang.core.psi.VcElementTypes.RIGHT_ASSOC_KW
import org.vclang.lang.core.psi.VcElementTypes.SET
import org.vclang.lang.core.psi.VcElementTypes.SIGMA_KW
import org.vclang.lang.core.psi.VcElementTypes.SUC_KW
import org.vclang.lang.core.psi.VcElementTypes.TRUNCATED_KW
import org.vclang.lang.core.psi.VcElementTypes.TRUNCATED_UNIVERSE
import org.vclang.lang.core.psi.VcElementTypes.UNIVERSE
import org.vclang.lang.core.psi.VcElementTypes.VIEW_KW
import org.vclang.lang.core.psi.VcElementTypes.WHERE_KW
import org.vclang.lang.core.psi.VcElementTypes.WITH_KW

class VcTokenType(debugName: String) : IElementType(debugName, VcLanguage)

val VC_KEYWORDS: TokenSet = TokenSet.create(
        OPEN_KW, EXPORT_KW, HIDING_KW, FUNCTION_KW, NON_ASSOC_KW,
        LEFT_ASSOC_KW, RIGHT_ASSOC_KW, PROP_KW, WHERE_KW, WITH_KW,
        ELIM_KW, NEW_KW, PI_KW, SIGMA_KW, LAM_KW, LET_KW,
        IN_KW, CASE_KW, WITH_KW, DATA_KW, CLASS_KW, EXTENDS_KW,
        VIEW_KW, ON_KW, BY_KW, INSTANCE_KW, TRUNCATED_KW,
        DEFAULT_KW, LP_KW, LH_KW, SUC_KW, MAX_KW,
        SET, UNIVERSE, TRUNCATED_UNIVERSE
)

val VC_COMMENTS: TokenSet = TokenSet.create(VcElementTypes.LINE_COMMENT, VcElementTypes.BLOCK_COMMENT)

val VC_WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
