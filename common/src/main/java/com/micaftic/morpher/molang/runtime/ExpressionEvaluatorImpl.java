package com.micaftic.morpher.molang.runtime;

import com.micaftic.morpher.molang.parser.ast.*;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public final class ExpressionEvaluatorImpl<TEntity> implements ExpressionEvaluator<TEntity>, ExpressionVisitor<Object> {

    private static final Double DOUBLE_ZERO = 0.0d;
    private static final Float FLOAT_ZERO = 0.0f;

    private static final Evaluator[] BINARY_EVALUATORS = {
            (evaluator, a, b) -> {
                if (!ValueConversions.asBoolean(a.visit(evaluator))) return Boolean.FALSE;
                return ValueConversions.asBoolean(b.visit(evaluator)) ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                if (ValueConversions.asBoolean(a.visit(evaluator))) return Boolean.TRUE;
                return ValueConversions.asBoolean(b.visit(evaluator)) ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av < bv ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av <= bv ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av > bv ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av >= bv ? Boolean.TRUE : Boolean.FALSE;
            },
            (evaluator, a, b) -> {
                final Object aVal = a.visit(evaluator);
                final Object bVal = b.visit(evaluator);
                return ValueConversions.asFloat(aVal) + ValueConversions.asFloat(bVal);
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av - bv;
            },
            (evaluator, a, b) -> {
                float av = ValueConversions.asFloat(a.visit(evaluator));
                float bv = ValueConversions.asFloat(b.visit(evaluator));
                return av * bv;
            },
            // molang 里除零结果为 0
            (evaluator, a, b) -> {
                float dividend = ValueConversions.asFloat(a.visit(evaluator));
                float divisor = ValueConversions.asFloat(b.visit(evaluator));
                if (divisor == 0.0f) return FLOAT_ZERO;
                return dividend / divisor;
            },
            (evaluator, a, b) -> { // arrow
                Object val = a.visit(evaluator);
                if (val == null) {
                    return null;
                }
                ExpressionEvaluatorImpl child = evaluator.createChild(val);
                Object res = b.visit(child);
                evaluator.returnValue = child.returnValue;
                return res;
            },
            (evaluator, a, b) -> { // null coalesce
                Object val = a.visit(evaluator);
                if (val == null) {
                    return b.visit(evaluator);
                } else {
                    return val;
                }
            },
            (evaluator, a, b) -> { // assignation
                Object val = b.visit(evaluator);
                if (a instanceof AssignableVariableExpression) {
                    AssignableVariable var = ((AssignableVariableExpression) a).target();
                    if (val instanceof Struct) {
                        val = ((Struct) val).copy();
                    }
                    var.assign(evaluator, val);
                } else if (a instanceof StructAccessExpression exp) {
                    if (val instanceof Struct) {
                        // 不允许结构体嵌套
                        return val;
                    }
                    Object value = exp.left().visit(evaluator);
                    if (value instanceof Struct) {
                        ((Struct) value).putProperty(exp.path(), val);
                    } else if (exp.left() instanceof AssignableVariableExpression) {
                        AssignableVariable variable = ((AssignableVariableExpression) exp.left()).target();
                        Struct struct = new HashMapStruct();
                        struct.putProperty(exp.path(), val);
                        variable.assign(evaluator, struct);
                    }
                }
                // TODO: (else case) This isn't fail-fast, we can only assign to access expressions
                return val;
            },
            (evaluator, a, b) -> { // conditional
                Object condition = a.visit(evaluator);
                if (ValueConversions.asBoolean(condition)) {
                    return b.visit(evaluator);
                }
                return null;
            }, (evaluator, a, b) -> {
                Object left = a.visit(evaluator);
                Object right = b.visit(evaluator);
                if (left == right)
                    return true;
                if (left instanceof Number || right instanceof Number)
                    return ValueConversions.asFloat(right) == ValueConversions.asFloat(left);
                if (left == null || right == null)
                    return false;
                if (left instanceof StringExpression)
                    return left.equals(right);
                if (right instanceof StringExpression)
                    return right.equals(left);
                return false;
            }, //eq
            (evaluator, a, b) -> {
                Object left = a.visit(evaluator);
                Object right = b.visit(evaluator);
                if (left == right)
                    return false;
                if (left instanceof Number || right instanceof Number)
                    return ValueConversions.asFloat(right) != ValueConversions.asFloat(left);
                if (left == null || right == null)
                    return true;
                if (left instanceof StringExpression)
                    return !left.equals(right);
                if (right instanceof StringExpression)
                    return !right.equals(left);
                return false;
            }
    };

    private final TEntity entity;

    private @Nullable Object returnValue;

    @Nullable
    private StatementExpression.Op op;

    private int cnt = 0;

    private int working = 0;

    public ExpressionEvaluatorImpl(@Nullable TEntity tentity) {
        this.entity = tentity;
    }

    @Override
    public TEntity entity() {
        return this.entity;
    }

    @Override
    @Nullable
    public Object eval(@NotNull Expression expression) {
        try {
            return expression.visit(this);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    @Override
    public float evalAsFloat(@NotNull Expression expression) {
        try {
            return evalFloat(expression);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    @Override
    public boolean evalAsBoolean(@NotNull Expression expression) {
        try {
            return evalBool(expression);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    // 算术子树原生递归，跳过中间 Float 装箱；遇到不能在 primitive 域处理的节点回退到 visit
    private float evalFloat(@NotNull Expression expr) {
        if (expr instanceof FloatExpression fe) {
            return fe.value();
        }
        if (expr instanceof BinaryExpression be) {
            switch (be.op()) {
                case ADD: return evalFloat(be.left()) + evalFloat(be.right());
                case SUB: return evalFloat(be.left()) - evalFloat(be.right());
                case MUL: return evalFloat(be.left()) * evalFloat(be.right());
                case DIV: {
                    float d = evalFloat(be.right());
                    if (d == 0.0f) return 0.0f;
                    return evalFloat(be.left()) / d;
                }
                case LT:  return evalFloat(be.left()) <  evalFloat(be.right()) ? 1.0f : 0.0f;
                case LTE: return evalFloat(be.left()) <= evalFloat(be.right()) ? 1.0f : 0.0f;
                case GT:  return evalFloat(be.left()) >  evalFloat(be.right()) ? 1.0f : 0.0f;
                case GTE: return evalFloat(be.left()) >= evalFloat(be.right()) ? 1.0f : 0.0f;
                case AND: return (evalBool(be.left()) && evalBool(be.right())) ? 1.0f : 0.0f;
                case OR:  return (evalBool(be.left()) || evalBool(be.right())) ? 1.0f : 0.0f;
                default: break;
            }
        }
        if (expr instanceof UnaryExpression ue) {
            switch (ue.op()) {
                case ARITHMETICAL_NEGATION: return -evalFloat(ue.expression());
                case PLUS: return evalFloat(ue.expression());
                case LOGICAL_NEGATION: return evalBool(ue.expression()) ? 0.0f : 1.0f;
                case RETURN: return evalFloat(ue.expression());
                default: break;
            }
        }
        if (expr instanceof TernaryConditionalExpression te) {
            return evalBool(te.condition())
                    ? evalFloat(te.trueExpression())
                    : evalFloat(te.falseExpression());
        }
        // 函数调用走 primitive evaluateFloat，整棵子树避免落回 Object/visit 装箱路径
        if (expr instanceof CallExpression ce) {
            return ce.function().evaluateFloat(this, ce.arguments());
        }
        // 变量读取走 primitive evaluateFloat（query/scoped 变量）
        if (expr instanceof VariableExpression ve) {
            return ve.target().evaluateFloat(this);
        }
        if (expr instanceof AssignableVariableExpression ave) {
            return ave.target().evaluateFloat(this);
        }
        if (expr instanceof StructAccessExpression se) {
            Object value = se.left().visit(this);
            if (value instanceof Struct) {
                return ValueConversions.asFloat(((Struct) value).getProperty(se.path()));
            }
            return 0.0f;
        }
        return ValueConversions.asFloat(expr.visit(this));
    }

    private boolean evalBool(@NotNull Expression expr) {
        if (expr instanceof FloatExpression fe) {
            return fe.value() != 0.0f;
        }
        if (expr instanceof BinaryExpression be) {
            switch (be.op()) {
                case AND: return evalBool(be.left()) && evalBool(be.right());
                case OR:  return evalBool(be.left()) || evalBool(be.right());
                case LT:  return evalFloat(be.left()) <  evalFloat(be.right());
                case LTE: return evalFloat(be.left()) <= evalFloat(be.right());
                case GT:  return evalFloat(be.left()) >  evalFloat(be.right());
                case GTE: return evalFloat(be.left()) >= evalFloat(be.right());
                case ADD: return (evalFloat(be.left()) + evalFloat(be.right())) != 0.0f;
                case SUB: return (evalFloat(be.left()) - evalFloat(be.right())) != 0.0f;
                case MUL: {
                    float l = evalFloat(be.left());
                    if (l == 0.0f) return false;
                    return evalFloat(be.right()) != 0.0f;
                }
                case DIV: {
                    float r = evalFloat(be.right());
                    if (r == 0.0f) return false;
                    return (evalFloat(be.left()) / r) != 0.0f;
                }
                default: break;
            }
        }
        if (expr instanceof UnaryExpression ue) {
            switch (ue.op()) {
                case LOGICAL_NEGATION: return !evalBool(ue.expression());
                case ARITHMETICAL_NEGATION: return evalBool(ue.expression());
                case PLUS: return evalBool(ue.expression());
                case RETURN: return evalBool(ue.expression());
                default: break;
            }
        }
        if (expr instanceof TernaryConditionalExpression te) {
            return evalBool(te.condition())
                    ? evalBool(te.trueExpression())
                    : evalBool(te.falseExpression());
        }
        return ValueConversions.asBoolean(expr.visit(this));
    }

    @Override
    @Nullable
    public Object evalAll(@NotNull Iterable<Expression> iterable, boolean z) {
        if (z) {
            this.working++;
        }
        Object objValueOf = DOUBLE_ZERO;
        try {
            if (iterable instanceof List<Expression> list) {
                final int size = list.size();
                for (int i = 0; i < size; i++) {
                    objValueOf = list.get(i).visit(this);
                    Object obj = popReturnValue();
                    if (obj != null) {
                        objValueOf = obj;
                        break;
                    }
                }
            } else {
                for (Expression expression : iterable) {
                    objValueOf = expression.visit(this);
                    Object obj = popReturnValue();
                    if (obj != null) {
                        objValueOf = obj;
                        break;
                    }
                }
            }
            return objValueOf;
        } finally {
            this.returnValue = null;
            this.op = null;
            if (z) {
                this.working--;
            }
        }
    }

    @NotNull
    public <TNewEntity> ExpressionEvaluatorImpl<TNewEntity> createChild(@Nullable TNewEntity tnewentity) {
        return new ExpressionEvaluatorImpl<>(tnewentity);
    }

    @Nullable
    private Object popReturnValue() {
        Object obj = this.returnValue;
        if (this.working == 0) {
            this.returnValue = null;
        }
        return obj;
    }

    @Override
    @Nullable
    public Object visitCall(@NotNull CallExpression expression) {
        return expression.function().evaluate(this, expression.arguments());
    }

    @Override
    public Object visitFloat(@NotNull FloatExpression floatExpression) {
        return floatExpression.boxed();
    }

    @Override
    public Object visitExecutionScope(@NotNull ExecutionScopeExpression executionScope) {
        Object objMo2074xaffeef43 = null;
        final List<Expression> expressions = executionScope.expressions();
        final int size = expressions.size();
        for (int i = 0; i < size; i++) {
            objMo2074xaffeef43 = expressions.get(i).visit(this);
            Object obj = popReturnValue();
            if (obj != null) {
                return obj;
            }
            if (this.cnt > 0 && this.op != null) {
                return null;
            }
        }
        return objMo2074xaffeef43;
    }

    private boolean buildExecutionScope(@NotNull ExecutionScopeExpression executionScope) {
        this.cnt++;
        try {
            final List<Expression> expressions = executionScope.expressions();
            final int size = expressions.size();
            for (int i = 0; i < size; i++) {
                expressions.get(i).visit(this);
                if (popReturnValue() != null) {
                    return true;
                }
                StatementExpression.Op op = this.op;
                this.op = null;
                if (op == StatementExpression.Op.CONTINUE) {
                    break;
                }
                if (op == StatementExpression.Op.BREAK) {
                    this.cnt--;
                    return true;
                }
            }
            this.cnt--;
            return false;
        } finally {
            this.cnt--;
        }
    }

    public void loopFunciton(@NotNull ExecutionScopeExpression executionScope, int n) {
        for (int i = 0; i < n && !buildExecutionScope(executionScope); i++) {
        }
    }

    public void forEachFunction(@NotNull ExecutionScopeExpression executionScope, AssignableVariable variableAccess, Iterable<?> iterable) {
        Iterator<?> it = iterable.iterator();
        while (it.hasNext()) {
            variableAccess.assign(this, it.next());
            if (buildExecutionScope(executionScope)) {
                return;
            }
        }
    }

    @Override
    public Object visitIdentifier(@NotNull IdentifierExpression identifierExpression) {
        throw new RuntimeException("Unknown identifier type");
    }

    @Override
    public Object visitVariable(@NotNull VariableExpression expression) {
        return expression.target().evaluate(this);
    }

    @Override
    public Object visitAssignableVariable(@NotNull AssignableVariableExpression expression) {
        return expression.target().evaluate(this);
    }

    @Override
    public Object visitStruct(@NotNull StructAccessExpression expression) {
        Object value = expression.left().visit(this);
        if (value instanceof Struct) {
            return ((Struct) value).getProperty(expression.path());
        } else {
            return null;
        }
    }

    @Override
    public Object visitBinary(@NotNull BinaryExpression expression) {
        return BINARY_EVALUATORS[expression.op().index()].eval(
                this,
                expression.left(),
                expression.right()
        );
    }

    @Override
    public Object visitBinaryOperation(BinaryOperationExpression expression) {
        Object objMo2074xaffeef43 = expression.getLeft().visit(this);
        Object objMo2074xaffeef432 = expression.getRight().visit(this);
        if (objMo2074xaffeef432 instanceof Number) {
            int iIntValue = ((Number) objMo2074xaffeef432).intValue();
            if (iIntValue < 0) {
                iIntValue = 0;
            }
            if (objMo2074xaffeef43 instanceof List list) {
                if (list.size() > iIntValue) {
                    return list.get(iIntValue);
                }
                return null;
            }
            return null;
        }
        return null;
    }

    @Override
    public Object visitUnary(@NotNull UnaryExpression expression) {
        Object value = expression.expression().visit(this);
        switch (expression.op()) {
            case LOGICAL_NEGATION:
                return ValueConversions.asBoolean(value) ? Boolean.FALSE : Boolean.TRUE;
            case ARITHMETICAL_NEGATION:
                return -ValueConversions.asFloat(value);
            case RETURN: {
                this.returnValue = value;
                return DOUBLE_ZERO;
            }
            default:
                throw new IllegalStateException("Unknown operation");
        }
    }

    @Override
    public Object visitStatement(@NotNull StatementExpression expression) {
        switch (expression.op()) {
            case BREAK: {
                this.op = StatementExpression.Op.BREAK;
                break;
            }
            case CONTINUE: {
                this.op = StatementExpression.Op.CONTINUE;
                break;
            }
        }
        return null;
    }

    @Override
    public Object visitString(@NotNull StringExpression expression) {
        return expression;
    }

    @Override
    public Object visitTernaryConditional(@NotNull TernaryConditionalExpression expression) {
        Object obj = expression.condition().visit(this);
        obj = ValueConversions.asBoolean(obj)
                ? expression.trueExpression().visit(this)
                : expression.falseExpression().visit(this);
        return obj;
    }

    @Override
    public Object visit(@NotNull Expression expression) {
        throw new UnsupportedOperationException("Unsupported expression type: " + expression);
    }

    private interface Evaluator<TEntity> {
        Object eval(ExpressionEvaluatorImpl<TEntity> evaluator, Expression a, Expression b);
    }
}