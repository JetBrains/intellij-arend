package com.jetbrains.arend.ide;

import com.intellij.lang.Language;

public class ArdlLanguage extends Language {
    public static final ArdlLanguage INSTANCE = new ArdlLanguage();

    protected ArdlLanguage() {
        super("LibLang");
    }
}
