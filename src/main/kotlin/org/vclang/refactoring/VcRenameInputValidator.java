package org.vclang.refactoring;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.vclang.psi.VcDefIdentifier;

/**
 * Created by user on 12/6/17.
 */
public class VcRenameInputValidator implements RenameInputValidator {
  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiElement(VcDefIdentifier.class);
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return VcNamesValidator.Companion.isPrefixName(newName);
  }
}
