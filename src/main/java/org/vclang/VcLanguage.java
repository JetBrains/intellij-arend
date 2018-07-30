package org.vclang;

import com.intellij.lang.Language;

public class VcLanguage extends Language {
  public static final VcLanguage INSTANCE = new VcLanguage();

  protected VcLanguage() {
    super("Vclang");
  }
}
