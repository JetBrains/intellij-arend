package org.vclang.module;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.vclang.module.util.VcModuleBuilder;

public abstract class VcModuleTypeBase extends ModuleType<VcModuleBuilder> {
    protected VcModuleTypeBase(@NotNull String id) {
        super(id);
    }
}
