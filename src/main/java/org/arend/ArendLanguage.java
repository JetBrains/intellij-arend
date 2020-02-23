package org.arend;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class ArendLanguage extends Language {
  @NotNull
  public static final ArendLanguage INSTANCE = new ArendLanguage();

  protected ArendLanguage() {
    super("Arend");
  }
}
