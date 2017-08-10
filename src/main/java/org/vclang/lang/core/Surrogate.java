package org.vclang.lang.core;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.binding.inference.InferenceLevelVariable;
import com.jetbrains.jetpad.vclang.core.context.binding.inference.InferenceVariable;
import com.jetbrains.jetpad.vclang.frontend.AbstractCompareVisitor;
import com.jetbrains.jetpad.vclang.frontend.Concrete;
import com.jetbrains.jetpad.vclang.frontend.resolving.HasOpens;
import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand;
import com.jetbrains.jetpad.vclang.module.ModulePath;
import com.jetbrains.jetpad.vclang.module.source.SourceId;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor;
import com.jetbrains.jetpad.vclang.term.AbstractExpressionVisitor;
import com.jetbrains.jetpad.vclang.term.AbstractLevelExpressionVisitor;
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrintVisitor;
import org.vclang.lang.core.psi.ext.adapters.ClassFieldAdapter;
import org.vclang.lang.core.psi.ext.adapters.ConstructorAdapter;
import org.vclang.lang.core.psi.ext.adapters.DefinitionAdapter;
import org.vclang.lang.core.psi.ext.adapters.ImplementationAdapter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Surrogate {
    private Surrogate() {
    }

    public interface PatternContainer extends Abstract.PatternContainer {
        @Override
        List<Pattern> getPatterns();

        default void replaceWithConstructor(int index, Abstract.Constructor constructor) {
            Pattern old = getPatterns().get(index);
            Pattern newPattern = new ConstructorPattern(old.getPosition(), constructor, Collections.emptyList());
            newPattern.setExplicit(old.isExplicit());
            getPatterns().set(index, newPattern);
        }
    }

    public interface StatementCollection extends Abstract.DefinitionCollection, HasOpens {
        List<? extends Statement> getGlobalStatements();

        @Nonnull
        @Override
        default Collection<? extends Abstract.Definition> getGlobalDefinitions() {
            return getGlobalStatements().stream().flatMap(s -> {
                if (s instanceof DefineStatement) {
                    return Stream.of(((DefineStatement) s).getDefinition());
                } else {
                    return Stream.empty();
                }
            }).collect(Collectors.toList());
        }

        @Nonnull
        @Override
        default Iterable<OpenCommand> getOpens() {
            return getGlobalStatements().stream().flatMap(s -> {
                if (s instanceof NamespaceCommandStatement) {
                    return Stream.of((NamespaceCommandStatement) s);
                } else {
                    return Stream.empty();
                }
            }).collect(Collectors.toList());
        }
    }

    // Parameters

    public static class Position {
        public final SourceId module;
        public final PsiElement element;

        public Position(SourceId module, PsiElement element) {
            this.module = module;
            this.element = element;
        }

        @Override
        public String toString() {
            return element.getText();
        }
    }

    public static class SourceNode implements Abstract.SourceNode {
        private final Position myPosition;

        public SourceNode(Position position) {
            myPosition = position;
        }

        public Position getPosition() {
            return myPosition;
        }
    }

    public static class Parameter extends SourceNode implements Abstract.Parameter {
        private boolean myExplicit;

        public Parameter(Position position, boolean explicit) {
            super(position);
            myExplicit = explicit;
        }

        @Override
        public boolean getExplicit() {
            return myExplicit;
        }

        public void setExplicit(boolean explicit) {
            myExplicit = explicit;
        }
    }

    public static class NameParameter extends Parameter implements Abstract.NameParameter, Abstract.ReferableSourceNode {
        private final String myName;

        public NameParameter(Position position, boolean explicit, String name) {
            super(position, explicit);
            myName = name;
        }

        @Override
        public String getName() {
            return myName;
        }
    }

    // Expressions

    public static class TypeParameter extends Parameter implements Abstract.TypeParameter {
        private final Expression myType;

        public TypeParameter(Position position, boolean explicit, Expression type) {
            super(position, explicit);
            myType = type;
        }

        public TypeParameter(boolean explicit, Expression type) {
            this(type.getPosition(), explicit, type);
        }

        @Override
        public Expression getType() {
            return myType;
        }
    }

    public static class TelescopeParameter extends TypeParameter implements Abstract.TelescopeParameter {
        private final List<? extends Abstract.ReferableSourceNode> myReferableList;

        public TelescopeParameter(Position position, boolean explicit, List<? extends Abstract.ReferableSourceNode> referableList, Expression type) {
            super(position, explicit, type);
            myReferableList = referableList;
        }

        @Override
        public List<? extends Abstract.ReferableSourceNode> getReferableList() {
            return myReferableList;
        }
    }

    public static abstract class Expression extends SourceNode implements Abstract.Expression {
        public Expression(Position position) {
            super(position);
        }

        @Override
        public void setWellTyped(Map<Abstract.ReferableSourceNode, Binding> context, com.jetbrains.jetpad.vclang.core.expr.Expression wellTyped) {
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            accept(new PrettyPrintVisitor(builder, 0), Abstract.Expression.PREC);
            return builder.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Expression)) return false;
            return accept(new AbstractCompareVisitor(), (Expression) obj);
        }
    }

    public static class Argument implements Abstract.Argument {
        private final Expression myExpression;
        private final boolean myExplicit;

        public Argument(Expression expression, boolean explicit) {
            myExpression = expression;
            myExplicit = explicit;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }

        @Override
        public boolean isExplicit() {
            return myExplicit;
        }
    }

    public static class AppExpression extends Expression implements Abstract.AppExpression {
        private final Expression myFunction;
        private final Argument myArgument;

        public AppExpression(Position position, Expression function, Argument argument) {
            super(position);
            myFunction = function;
            myArgument = argument;
        }

        @Override
        public Expression getFunction() {
            return myFunction;
        }

        @Override
        public Argument getArgument() {
            return myArgument;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitApp(this, params);
        }
    }

    public static class BinOpSequenceExpression extends Expression implements Abstract.BinOpSequenceExpression {
        private final List<Abstract.BinOpSequenceElem> mySequence;
        private Expression myLeft;

        public BinOpSequenceExpression(Position position, Expression left, List<Abstract.BinOpSequenceElem> sequence) {
            super(position);
            myLeft = left;
            mySequence = sequence;
        }

        @Override
        public Expression getLeft() {
            return myLeft;
        }

        @Override
        public List<Abstract.BinOpSequenceElem> getSequence() {
            return mySequence;
        }

        public BinOpExpression makeBinOp(Abstract.Expression left, Abstract.Definition binOp, Abstract.ReferenceExpression var, Abstract.Expression right) {
            assert left instanceof Expression && right instanceof Expression && var instanceof Expression;
            return new BinOpExpression(((Expression) var).getPosition(), (Expression) left, binOp, (Expression) right);
        }

        public Expression makeError(Abstract.SourceNode node) {
            return new InferHoleExpression(((SourceNode) node).getPosition());
        }

        public void replace(Abstract.Expression expression) {
            assert expression instanceof Expression;
            myLeft = (Expression) expression;
            mySequence.clear();
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitBinOpSequence(this, params);
        }
    }

    public static class BinOpExpression extends Expression implements Abstract.BinOpExpression {
        private final Abstract.Definition myBinOp;
        private final Expression myLeft;
        private final Expression myRight;

        public BinOpExpression(Position position, Expression left, Abstract.Definition binOp, Expression right) {
            super(position);
            myLeft = left;
            myBinOp = binOp;
            myRight = right;
        }

        @Override
        public String getName() {
            return myBinOp.getName();
        }

        @Override
        public Abstract.Definition getReferent() {
            return myBinOp;
        }

        @Override
        public Expression getLeft() {
            return myLeft;
        }

        @Override
        public Expression getRight() {
            return myRight;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitBinOp(this, params);
        }
    }

    public static class ReferenceExpression extends Expression implements Abstract.ReferenceExpression {
        private final Expression myExpression;
        private final String myName;
        private Abstract.ReferableSourceNode myReferent;

        public ReferenceExpression(Position position, Expression expression, String name) {
            super(position);
            myExpression = expression;
            myName = name;
            myReferent = null;
        }

        public ReferenceExpression(Position position, Abstract.ReferableSourceNode referable) {
            super(position);
            myExpression = null;
            myName = referable.getName();
            myReferent = referable;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }

        @Override
        public Abstract.ReferableSourceNode getReferent() {
            return myReferent;
        }

        public void setResolvedReferent(Abstract.ReferableSourceNode referent) {
            myReferent = referent;
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitReference(this, params);
        }
    }

    public static class InferenceReferenceExpression extends Expression implements Abstract.InferenceReferenceExpression {
        private final InferenceVariable myVariable;

        public InferenceReferenceExpression(Position position, InferenceVariable variable) {
            super(position);
            myVariable = variable;
        }

        public InferenceVariable getVariable() {
            return myVariable;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitInferenceReference(this, params);
        }
    }

    public static class ModuleCallExpression extends Expression implements Abstract.ModuleCallExpression {
        private final ModulePath myPath;
        private Abstract.Definition myModule;

        public ModuleCallExpression(Position position, List<String> path) {
            super(position);
            this.myPath = new ModulePath(path);
        }

        @Override
        public ModulePath getPath() {
            return myPath;
        }

        @Override
        public Abstract.Definition getModule() {
            return myModule;
        }

        public void setModule(Abstract.Definition module) {
            myModule = module;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitModuleCall(this, params);
        }
    }

    public static class ClassExtExpression extends Expression implements Abstract.ClassExtExpression {
        private final Expression myBaseClassExpression;
        private final List<ClassFieldImpl> myDefinitions;

        public ClassExtExpression(Position position, Expression baseClassExpression, List<ClassFieldImpl> definitions) {
            super(position);
            myBaseClassExpression = baseClassExpression;
            myDefinitions = definitions;
        }

        @Override
        public Expression getBaseClassExpression() {
            return myBaseClassExpression;
        }

        @Override
        public List<ClassFieldImpl> getStatements() {
            return myDefinitions;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitClassExt(this, params);
        }
    }

    public static class ClassFieldImpl extends SourceNode implements Abstract.ClassFieldImpl {
        private final String myName;
        private final Expression myExpression;
        private Abstract.ClassField myImplementedField;

        public ClassFieldImpl(Position position, String identifier, Expression expression) {
            super(position);
            myName = identifier;
            myExpression = expression;
        }

        @Override
        public String getImplementedFieldName() {
            return myName;
        }

        @Override
        public Abstract.ClassField getImplementedField() {
            return myImplementedField;
        }

        public void setImplementedField(Abstract.ClassField newImplementedField) {
            myImplementedField = newImplementedField;
        }

        @Override
        public Expression getImplementation() {
            return myExpression;
        }
    }

    public static class NewExpression extends Expression implements Abstract.NewExpression {
        private final Expression myExpression;

        public NewExpression(Position position, Expression expression) {
            super(position);
            myExpression = expression;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitNew(this, params);
        }
    }

    public static class ErrorExpression extends Expression implements Abstract.ErrorExpression {
        public ErrorExpression(Position position) {
            super(position);
        }

        @Override
        public Expression getExpr() {
            return null;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitError(this, params);
        }
    }

    public static class InferHoleExpression extends Expression implements Abstract.InferHoleExpression {
        public InferHoleExpression(Position position) {
            super(position);
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitInferHole(this, params);
        }
    }

    public static class LamExpression extends Expression implements Abstract.LamExpression {
        private final List<Parameter> myParameters;
        private final Expression myBody;

        public LamExpression(Position position, List<Parameter> parameters, Expression body) {
            super(position);
            myParameters = parameters;
            myBody = body;
        }

        @Override
        public List<Parameter> getParameters() {
            return myParameters;
        }

        @Override
        public Expression getBody() {
            return myBody;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitLam(this, params);
        }
    }

    public static class LetClause extends SourceNode implements Abstract.LetClause, Abstract.ReferableSourceNode {
        private final List<Parameter> myParameters;
        private final Expression myResultType;
        private final Expression myTerm;
        private final String myName;

        public LetClause(Position position, String name, List<Parameter> parameters, Expression resultType, Expression term) {
            super(position);
            myParameters = parameters;
            myResultType = resultType;
            myTerm = term;
            myName = name;
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public Abstract.Expression getTerm() {
            return myTerm;
        }

        @Override
        public List<Parameter> getParameters() {
            return myParameters;
        }

        @Override
        public Abstract.Expression getResultType() {
            return myResultType;
        }
    }

    public static class LetExpression extends Expression implements Abstract.LetExpression {
        private final List<LetClause> myClauses;
        private final Expression myExpression;

        public LetExpression(Position position, List<LetClause> clauses, Expression expression) {
            super(position);
            myClauses = clauses;
            myExpression = expression;
        }

        @Override
        public List<LetClause> getClauses() {
            return myClauses;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitLet(this, params);
        }
    }

    public static class PiExpression extends Expression implements Abstract.PiExpression {
        private final List<TypeParameter> myArguments;
        private final Expression myCodomain;

        public PiExpression(Position position, List<TypeParameter> arguments, Expression codomain) {
            super(position);
            myArguments = arguments;
            myCodomain = codomain;
        }

        @Override
        public List<TypeParameter> getParameters() {
            return myArguments;
        }

        @Override
        public Expression getCodomain() {
            return myCodomain;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitPi(this, params);
        }
    }

    public static class SigmaExpression extends Expression implements Abstract.SigmaExpression {
        private final List<TypeParameter> myArguments;

        public SigmaExpression(Position position, List<TypeParameter> arguments) {
            super(position);
            myArguments = arguments;
        }

        @Override
        public List<TypeParameter> getParameters() {
            return myArguments;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitSigma(this, params);
        }
    }

    public static class TupleExpression extends Expression implements Abstract.TupleExpression {
        private final List<Expression> myFields;

        public TupleExpression(Position position, List<Expression> fields) {
            super(position);
            myFields = fields;
        }

        @Override
        public List<Expression> getFields() {
            return myFields;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitTuple(this, params);
        }
    }

    public static class UniverseExpression extends Expression implements Abstract.UniverseExpression {
        private final LevelExpression myPLevel;
        private final LevelExpression myHLevel;

        public UniverseExpression(Position position, LevelExpression pLevel, LevelExpression hLevel) {
            super(position);
            myPLevel = pLevel;
            myHLevel = hLevel;
        }

        @Override
        public LevelExpression getPLevel() {
            return myPLevel;
        }

        @Override
        public LevelExpression getHLevel() {
            return myHLevel;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitUniverse(this, params);
        }
    }

    public static class ProjExpression extends Expression implements Abstract.ProjExpression {
        private final Expression myExpression;
        private final int myField;

        public ProjExpression(Position position, Expression expression, int field) {
            super(position);
            myExpression = expression;
            myField = field;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }

        @Override
        public int getField() {
            return myField;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitProj(this, params);
        }
    }

    public static class CaseExpression extends Expression implements Abstract.CaseExpression {
        private final List<Expression> myExpressions;
        private final List<FunctionClause> myClauses;

        public CaseExpression(Position position, List<Expression> expressions, List<FunctionClause> clauses) {
            super(position);
            myExpressions = expressions;
            myClauses = clauses;
        }

        @Override
        public List<Expression> getExpressions() {
            return myExpressions;
        }

        @Override
        public List<FunctionClause> getClauses() {
            return myClauses;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitCase(this, params);
        }
    }

    public static class FunctionClause extends SourceNode implements Abstract.FunctionClause, PatternContainer {
        private final List<Pattern> myPatterns;
        private final Expression myExpression;

        public FunctionClause(Position position, List<Pattern> patterns, Expression expression) {
            super(position);
            myPatterns = patterns;
            myExpression = expression;
        }

        @Override
        public List<Pattern> getPatterns() {
            return myPatterns;
        }

        @Override
        public Expression getExpression() {
            return myExpression;
        }
    }

    // Level expressions

    public static class NumericLiteral extends Expression implements Abstract.NumericLiteral {
        private final int myNumber;

        public NumericLiteral(Position position, int number) {
            super(position);
            myNumber = number;
        }

        @Override
        public int getNumber() {
            return myNumber;
        }

        @Override
        public <P, R> R accept(AbstractExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitNumericLiteral(this, params);
        }
    }

    public static abstract class LevelExpression extends SourceNode implements Abstract.LevelExpression {
        protected LevelExpression(Position position) {
            super(position);
        }
    }

    public static class InferVarLevelExpression extends LevelExpression implements Abstract.InferVarLevelExpression {
        private final InferenceLevelVariable myVariable;

        public InferVarLevelExpression(InferenceLevelVariable variable) {
            super(new Position(() -> new ModulePath("$transient$"), null));
            myVariable = variable;
        }

        @Override
        public InferenceLevelVariable getVariable() {
            return myVariable;
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitVar(this, params);
        }
    }

    public static class PLevelExpression extends LevelExpression implements Abstract.PLevelExpression {
        public PLevelExpression(Position position) {
            super(position);
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitLP(this, params);
        }
    }

    public static class HLevelExpression extends LevelExpression implements Abstract.HLevelExpression {
        public HLevelExpression(Position position) {
            super(position);
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitLH(this, params);
        }
    }

    public static class InfLevelExpression extends LevelExpression implements Abstract.InfLevelExpression {
        public InfLevelExpression(Position position) {
            super(position);
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitInf(this, params);
        }
    }

    public static class NumberLevelExpression extends LevelExpression implements Abstract.NumberLevelExpression {
        private final int myNumber;

        public NumberLevelExpression(Position position, int number) {
            super(position);
            myNumber = number;
        }

        @Override
        public int getNumber() {
            return myNumber;
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitNumber(this, params);
        }
    }

    public static class SucLevelExpression extends LevelExpression implements Abstract.SucLevelExpression {
        private final LevelExpression myExpression;

        public SucLevelExpression(Position position, LevelExpression expression) {
            super(position);
            myExpression = expression;
        }

        @Override
        public LevelExpression getExpression() {
            return myExpression;
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitSuc(this, params);
        }
    }

    public static class MaxLevelExpression extends LevelExpression implements Abstract.MaxLevelExpression {
        private final LevelExpression myLeft;
        private final LevelExpression myRight;

        public MaxLevelExpression(Position position, LevelExpression left, LevelExpression right) {
            super(position);
            myLeft = left;
            myRight = right;
        }

        @Override
        public LevelExpression getLeft() {
            return myLeft;
        }

        @Override
        public LevelExpression getRight() {
            return myRight;
        }

        @Override
        public <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitMax(this, params);
        }
    }

    // Definitions

    public static class LocalVariable extends SourceNode implements Abstract.ReferableSourceNode {
        private final String myName;

        public LocalVariable(Position position, String name) {
            super(position);
            myName = name;
        }

        @Override
        public String getName() {
            return myName;
        }
    }

    public static abstract class SignatureDefinition extends DefinitionAdapter {
        private List<Parameter> myParameters = null;
        private Expression myResultType = null;

        public SignatureDefinition(@Nonnull ASTNode node) {
            super(node);
        }

        public SignatureDefinition reconstruct(Position position, String name, Abstract.Precedence precedence, List<Parameter> parameters, Expression resultType) {
            super.reconstruct(position, name, precedence);
            myParameters = parameters;
            myResultType = resultType;
            return this;
        }

        public List<? extends Parameter> getParameters() {
            return myParameters;
        }

        public Expression getResultType() {
            return myResultType;
        }
    }

    public static class SuperClass extends SourceNode implements Abstract.SuperClass {
        private final Expression mySuperClass;

        public SuperClass(Position position, Expression superClass) {
            super(position);
            mySuperClass = superClass;
        }

        @Override
        public Expression getSuperClass() {
            return mySuperClass;
        }
    }

    public static abstract class FunctionBody extends SourceNode implements Abstract.FunctionBody {
        public FunctionBody(Position position) {
            super(position);
        }
    }

    public static class TermFunctionBody extends FunctionBody implements Abstract.TermFunctionBody {
        private final Expression myTerm;

        public TermFunctionBody(Position position, Expression term) {
            super(position);
            myTerm = term;
        }

        @Override
        public Expression getTerm() {
            return myTerm;
        }
    }

    public static class ElimFunctionBody extends FunctionBody implements Abstract.ElimFunctionBody {
        private final List<ReferenceExpression> myExpressions;
        private final List<FunctionClause> myClauses;

        public ElimFunctionBody(Position position, List<ReferenceExpression> expressions, List<FunctionClause> clauses) {
            super(position);
            myExpressions = expressions;
            myClauses = clauses;
        }

        @Override
        public List<? extends ReferenceExpression> getEliminatedReferences() {
            return myExpressions;
        }

        @Override
        public List<? extends FunctionClause> getClauses() {
            return myClauses;
        }
    }

    public static class ConstructorClause extends SourceNode implements Abstract.ConstructorClause, PatternContainer {
        private final List<Pattern> myPatterns;
        private final List<ConstructorAdapter> myConstructors;

        public ConstructorClause(Position position, List<Pattern> patterns, List<ConstructorAdapter> constructors) {
            super(position);
            myPatterns = patterns;
            myConstructors = constructors;
        }

        @Override
        public List<Pattern> getPatterns() {
            return myPatterns;
        }

        @Override
        public List<ConstructorAdapter> getConstructors() {
            return myConstructors;
        }
    }

    public static class ClassDefinition extends Concrete.Definition implements Abstract.ClassDefinition, StatementCollection {
        private final List<TypeParameter> myPolyParameters;
        private final List<SuperClass> mySuperClasses;
        private final List<ClassFieldAdapter> myFields;
        private final List<ImplementationAdapter> myImplementations;
        private final List<Statement> myGlobalStatements;
        private final List<DefinitionAdapter> myInstanceDefinitions;

        public ClassDefinition(Position position, String name, List<TypeParameter> polyParams, List<SuperClass> superClasses, List<ClassFieldAdapter> fields, List<ImplementationAdapter> implementations, List<Statement> globalStatements, List<DefinitionAdapter> instanceDefinitions) {
            super(new Concrete.Position(position.module, 0, 0), name, Abstract.Precedence.DEFAULT);
            myPolyParameters = polyParams;
            mySuperClasses = superClasses;
            myFields = fields;
            myImplementations = implementations;
            myGlobalStatements = globalStatements;
            myInstanceDefinitions = instanceDefinitions;
        }

        public ClassDefinition(Position position, String name, List<Statement> globalStatements) {
            this(position, name, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), globalStatements, Collections.emptyList());
        }

        @Override
        public <P, R> R accept(AbstractDefinitionVisitor<? super P, ? extends R> visitor, P params) {
            return visitor.visitClass(this, params);
        }

        @Override
        public List<TypeParameter> getPolyParameters() {
            return myPolyParameters;
        }

        @Override
        public List<SuperClass> getSuperClasses() {
            return mySuperClasses;
        }

        @Override
        public List<ClassFieldAdapter> getFields() {
            return myFields;
        }

        @Override
        public List<ImplementationAdapter> getImplementations() {
            return myImplementations;
        }

        @Override
        public List<DefinitionAdapter> getInstanceDefinitions() {
            return myInstanceDefinitions;
        }

        @Override
        public List<? extends Statement> getGlobalStatements() {
            return myGlobalStatements;
        }
    }

    // Statements

    public static abstract class Statement extends SourceNode {
        public Statement(Position position) {
            super(position);
        }
    }

    public static class DefineStatement extends Statement {
        private final DefinitionAdapter myDefinition;

        public DefineStatement(Position position, DefinitionAdapter definition) {
            super(position);
            myDefinition = definition;
        }

        public DefinitionAdapter getDefinition() {
            return myDefinition;
        }
    }

    public static class NamespaceCommandStatement extends Statement implements OpenCommand {
        private final ModulePath myModulePath;
        private final List<String> myPath;
        private final boolean myHiding;
        private final List<String> myNames;
        private Abstract.Definition myDefinition;

        public NamespaceCommandStatement(Position position, Kind kind, List<String> modulePath, List<String> path, boolean isHiding, List<String> names) {
            super(position);
            myDefinition = null;
            myModulePath = modulePath != null ? new ModulePath(modulePath) : null;
            myPath = path;
            myHiding = isHiding;
            myNames = names;
        }

        @Override
        public @Nullable
        ModulePath getModulePath() {
            return myModulePath;
        }

        @Override
        public @Nonnull
        List<String> getPath() {
            return myPath;
        }

        @Override
        public Abstract.Definition getResolvedClass() {
            return myDefinition;
        }

        public void setResolvedClass(Abstract.Definition resolvedClass) {
            myDefinition = resolvedClass;
        }

        @Override
        public boolean isHiding() {
            return myHiding;
        }

        @Override
        public @Nullable
        List<String> getNames() {
            return myNames;
        }

        public enum Kind {OPEN, EXPORT}

    }

    // Patterns

    public static abstract class Pattern extends SourceNode implements Abstract.Pattern {
        private boolean myExplicit;

        public Pattern(Position position) {
            super(position);
            myExplicit = true;
        }

        @Override
        public boolean isExplicit() {
            return myExplicit;
        }

        public void setExplicit(boolean isExplicit) {
            myExplicit = isExplicit;
        }
    }

    public static class NamePattern extends Pattern implements Abstract.NamePattern, Abstract.ReferableSourceNode {
        private final String myName;

        public NamePattern(Position position, String name) {
            super(position);
            myName = name;
        }

        public NamePattern(Position position, boolean isExplicit, String name) {
            super(position);
            setExplicit(isExplicit);
            myName = name;
        }

        @Override
        public String getName() {
            return myName;
        }
    }

    public static class ConstructorPattern extends Pattern implements Abstract.ConstructorPattern, PatternContainer {
        private final String myConstructorName;
        private final List<Pattern> myArguments;
        private Abstract.Constructor myConstructor;

        public ConstructorPattern(Position position, String constructorName, List<Pattern> arguments) {
            super(position);
            myConstructorName = constructorName;
            myArguments = arguments;
        }

        public ConstructorPattern(Position position, boolean isExplicit, String constructorName, List<Pattern> arguments) {
            super(position);
            setExplicit(isExplicit);
            myConstructorName = constructorName;
            myArguments = arguments;
        }

        public ConstructorPattern(Position position, Abstract.Constructor constructor, List<Pattern> arguments) {
            super(position);
            myConstructor = constructor;
            myConstructorName = constructor.getName();
            myArguments = arguments;
        }

        public ConstructorPattern(Position position, boolean isExplicit, Abstract.Constructor constructor, List<Pattern> arguments) {
            super(position);
            setExplicit(isExplicit);
            myConstructor = constructor;
            myConstructorName = constructor.getName();
            myArguments = arguments;
        }

        @Override
        public String getConstructorName() {
            return myConstructorName;
        }

        @Override
        public Abstract.Constructor getConstructor() {
            return myConstructor;
        }

        public void setConstructor(Abstract.Constructor constructor) {
            myConstructor = constructor;
        }

        @Override
        public List<Pattern> getPatterns() {
            return myArguments;
        }
    }

    public static class EmptyPattern extends Pattern implements Abstract.EmptyPattern {
        public EmptyPattern(Position position) {
            super(position);
        }

        public EmptyPattern(Position position, boolean isExplicit) {
            super(position);
            setExplicit(isExplicit);
        }
    }
}
