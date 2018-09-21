package org.arend.module;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.arend.module.util.ArendModuleBuilder;

public abstract class ArendModuleTypeBase extends ModuleType<ArendModuleBuilder> {
    protected ArendModuleTypeBase(@NotNull String id) {
        super(id);
    }
}
