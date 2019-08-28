package org.arend;

import com.intellij.lang.Language;

public class InjectionTextLanguage extends Language {
  public static final InjectionTextLanguage INSTANCE = new InjectionTextLanguage();

  protected InjectionTextLanguage() {
    super("INJECTION_TEXT");
  }
}
