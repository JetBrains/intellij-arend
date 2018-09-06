package com.jetbrains.arend.ide.module;

import com.intellij.openapi.module.ModuleType;
import com.jetbrains.arend.ide.module.util.ArdModuleBuilder;
import org.jetbrains.annotations.NotNull;

public abstract class ArdModuleTypeBase extends ModuleType<ArdModuleBuilder> {
    protected ArdModuleTypeBase(@NotNull String id) {
        super(id);
    }
}
