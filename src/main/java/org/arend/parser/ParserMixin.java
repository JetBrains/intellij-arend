package org.arend.parser;

import com.intellij.lang.PsiBuilder;

public class ParserMixin {
  private final static int MAX_RECURSION_LEVEL = 10000;

  static boolean recursion_guard_(PsiBuilder builder, int level, String funcName) {
    if (level > MAX_RECURSION_LEVEL) {
      builder.mark().error("Maximum recursion level (" + MAX_RECURSION_LEVEL + ") reached in '" + funcName + "'");
      return false;
    }
    return true;
  }
}
