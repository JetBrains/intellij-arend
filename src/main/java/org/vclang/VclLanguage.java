package org.vclang;

import com.intellij.lang.Language;

public class VclLanguage extends Language {
    public static final VclLanguage INSTANCE = new VclLanguage();

    protected VclLanguage() {
        super("LibLang");
    }
}
