package org.arend.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.tree.TokenSet;
import org.arend.psi.ArendElementTypes;

import static org.arend.parser.ArendParser.*;
import static org.arend.psi.ArendElementTypes.*;

public class ParserUtil extends GeneratedParserUtilBase {
  public static boolean exprWOBImpl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expr")) return false;
    addVariant(builder_, "<expr>");
    boolean result_, pinned_;
    PsiBuilder.Marker marker_ = enter_section_(builder_, level_, _NONE_, "<expr>");
    result_ = sigmaExpr(builder_, level_ + 1);
    if (!result_) result_ = piExprWOB(builder_, level_ + 1);
    if (!result_) result_ = lamExprWOB(builder_, level_ + 1);
    if (!result_) result_ = letExprWOB(builder_, level_ + 1);
    if (!result_) result_ = caseExpr(builder_, level_ + 1);
    if (!result_) result_ = newExprWOB(builder_, level_ + 1);
    pinned_ = result_;
    result_ = result_ && expr_0(builder_, level_ + 1, -1);
    exit_section_(builder_, level_, marker_, null, result_, pinned_, null);
    return result_ || pinned_;
  }
}
