package com.jetbrains.arend.ide;

import com.intellij.lang.Language;

public class ArdLanguage extends Language {
    public static final ArdLanguage INSTANCE = new ArdLanguage();

    protected ArdLanguage() {
        super("Arend");
    }
}
