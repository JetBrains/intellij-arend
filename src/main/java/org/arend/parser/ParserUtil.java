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

  static boolean maybeImplicitPattern(PsiBuilder builder, int level, Parser sequenceParser, Parser atomParser) {
    if (!recursion_guard_(builder, level, "implicitGuard")) return false;
    if (nextTokenIs(builder, LBRACE)) {
      boolean result_;
      PsiBuilder.Marker marker_ = enter_section_(builder, level, 0, ArendElementTypes.PATTERN, "<pattern>");
      result_ = consumeToken(builder, LBRACE);
      result_ = result_ && parseMaybeAtomPattern(builder, level, sequenceParser, atomParser, TokenSet.create(RBRACE));
      result_ = result_ && consumeToken(builder, RBRACE);
      exit_section_(builder, level, marker_, result_, false, null);
      return result_;
    } else {
      return false;
    }
  }

  private static boolean parseMaybeAtomPattern(PsiBuilder builder, int level, Parser sequenceParser, Parser atomParser, TokenSet set) {
    PsiBuilder.Marker mark = builder.mark();
    boolean result2 = atomParser.parse(builder, level);
    if (result2 && set.contains(builder.getTokenType())) {
      mark.drop();
      return true;
    } else {
      mark.rollbackTo();
      return sequenceParser.parse(builder, level + 1);
    }
  }

  public static boolean noMatch(PsiBuilder ignoredBuilder, int ignoredLevel) {
    return false;
  }
}
