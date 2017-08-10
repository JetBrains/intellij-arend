package org.vclang.ide.idea;

import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class VcModuleTypeBase extends ModuleType<VcModuleBuilder> {
    protected VcModuleTypeBase(@NotNull String id) {
        super(id);
    }

    // @Override
    public Icon getBigIcon() {
        return null;
    }
}
