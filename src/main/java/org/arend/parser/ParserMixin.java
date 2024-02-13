package org.arend.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.arend.ArendLanguage;
import org.arend.lexer.ArendDocLexerAdapter;
import org.arend.psi.ArendCompositeElementType;
import org.arend.psi.ArendTokenType;
import org.arend.psi.doc.ArendDocComment;
import org.jetbrains.annotations.NotNull;

public class ParserMixin {
  public static final ArendTokenType DOC_SINGLE_LINE_START = new ArendTokenType("-- |");
  public static final ArendTokenType DOC_START = new ArendTokenType("{- |");
  public static final ArendTokenType DOC_END = new ArendTokenType("-}");
  public static final ArendTokenType DOC_INLINE_CODE_BORDER = new ArendTokenType("DOC_INLINE_CODE_BORDER");
  public static final ArendTokenType DOC_TEXT = new ArendTokenType("DOC_TEXT");
  public static final ArendTokenType DOC_CODE = new ArendTokenType("DOC_CODE");
  public static final ArendTokenType DOC_CODE_LINE = new ArendTokenType("DOC_CODE_LINE");
  public static final ArendTokenType DOC_PARAGRAPH_SEP = new ArendTokenType("DOC_PARAGRAPH_SEP");
  public static final ArendTokenType DOC_LBRACKET = new ArendTokenType("[");
  public static final ArendTokenType DOC_RBRACKET = new ArendTokenType("]");
  public static final ArendTokenType DOC_CODE_BLOCK_BORDER = new ArendTokenType("```");
  public static final ArendTokenType DOC_NEWLINE_LATEX_CODE = new ArendTokenType("$$");
  public static final ArendTokenType DOC_INLINE_LATEX_CODE = new ArendTokenType("$");
  public static final ArendTokenType DOC_LATEX_CODE = new ArendTokenType("DOC_LATEX_CODE");
  public static final ArendTokenType DOC_ITALICS_CODE_BORDER = new ArendTokenType("*");
  public static final ArendTokenType DOC_ITALICS_CODE = new ArendTokenType("DOC_ITALICS_CODE");
  public static final ArendTokenType DOC_BOLD_CODE_BORDER = new ArendTokenType("**");
  public static final ArendTokenType DOC_BOLD_CODE = new ArendTokenType("DOC_BOLD_CODE");
  public static final ArendCompositeElementType DOC_NEWLINE = new ArendCompositeElementType("DOC_NEWLINE");
  public static final ArendCompositeElementType DOC_LINEBREAK = new ArendCompositeElementType("DOC_LINEBREAK");
  public static final ArendCompositeElementType DOC_TABS = new ArendCompositeElementType("DOC_TABS");
  public static final ArendCompositeElementType DOC_UNORDERED_LIST = new ArendCompositeElementType("DOC_UNORDERED_LIST");
  public static final ArendCompositeElementType DOC_ORDERED_LIST = new ArendCompositeElementType("DOC_ORDERED_LIST");
  public static final ArendCompositeElementType DOC_BLOCKQUOTES = new ArendCompositeElementType("DOC_BLOCKQUOTES");
  public static final ArendCompositeElementType DOC_HEADER_1 = new ArendCompositeElementType("DOC_HEADER_1");
  public static final ArendCompositeElementType DOC_HEADER_2 = new ArendCompositeElementType("DOC_HEADER_2");
  public static final ArendCompositeElementType DOC_CODE_BLOCK = new ArendCompositeElementType("DOC_CODE_BLOCK");
  public static final ArendCompositeElementType DOC_REFERENCE = new ArendCompositeElementType("DOC_REFERENCE");
  public static final ArendCompositeElementType DOC_REFERENCE_TEXT = new ArendCompositeElementType("DOC_REFERENCE_TEXT");

  public static final ILazyParseableElementType DOC_COMMENT = new ILazyParseableElementType("DOC_COMMENT", ArendLanguage.INSTANCE) {
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      return new ArendDocParser().parse(this, PsiBuilderFactory.getInstance().createBuilder(chameleon.getTreeParent().getPsi().getProject(), chameleon, new ArendDocLexerAdapter(), ArendLanguage.INSTANCE, chameleon.getText())).getFirstChildNode();
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new ArendDocComment(text);
    }
  };

  private final static int MAX_RECURSION_LEVEL = 10000;

  static boolean recursion_guard_(PsiBuilder builder, int level, String funcName) {
    if (level > MAX_RECURSION_LEVEL) {
      builder.mark().error("Maximum recursion level (" + MAX_RECURSION_LEVEL + ") reached in '" + funcName + "'");
      return false;
    }
    return true;
  }
}
