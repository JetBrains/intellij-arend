package org.arend;

import com.intellij.lang.Language;

public class ArendLanguage extends Language {
  public static final ArendLanguage INSTANCE = new ArendLanguage();

  protected ArendLanguage() {
    super("Arend");
  }
}
