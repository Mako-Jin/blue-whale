package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ProcessModelFlow;
import com.bluewhale.workflow.support.ValidationCode;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Map;

/**
 * 条件流转守卫：以 SpEL 受限上下文求值 {@code flows[].condition}。
 *
 * <p>安全约束：使用 {@link SimpleEvaluationContext}（只读数据绑定 + {@link MapAccessor}），
 * 禁用 Bean 引用、类型引用、构造器与任意方法调用；表达式长度上限 {@value #MAX_EXPRESSION_LENGTH}。
 * 变量既支持直接按键读取（{@code amount > 100}），也支持 {@code #amount > 100}。</p>
 *
 * <p>语法校验与运行期求值分离：{@link #isValid(String)} 只解析不求值，供定义校验器使用；
 * {@link #accept(GuardContext, ProcessModelFlow)} 在运行期求值，非法表达式或求值异常统一抛
 * {@link ProcessModelException}。</p>
 *
 * @author BlueWhale
 */
public final class ConditionGuard implements Guard {

    /** 条件表达式长度上限 */
    public static final int MAX_EXPRESSION_LENGTH = 512;

    /** SpEL 解析器（线程安全） */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /** 条件原文 */
    private final String source;

    /** 已解析表达式 */
    private final Expression expression;

    private ConditionGuard(String source, Expression expression) {
        this.source = source;
        this.expression = expression;
    }

    /**
     * 编译条件表达式。
     *
     * @param condition 条件表达式文本，不可为空
     * @return 条件守卫
     * @throws ProcessModelException 表达式超长或语法非法时抛出
     */
    public static ConditionGuard of(String condition) {
        if (condition == null || condition.isBlank()) {
            throw new ProcessModelException(ValidationCode.CONDITION_SYNTAX_INVALID, "流转条件不能为空");
        }
        if (condition.length() > MAX_EXPRESSION_LENGTH) {
            throw new ProcessModelException(ValidationCode.CONDITION_TOO_LONG,
                    "流转条件长度不能超过 " + MAX_EXPRESSION_LENGTH + " 字符");
        }
        try {
            return new ConditionGuard(condition, PARSER.parseExpression(condition));
        } catch (Exception e) {
            throw new ProcessModelException(ValidationCode.CONDITION_SYNTAX_INVALID,
                    "流转条件表达式语法非法：" + condition);
        }
    }

    /**
     * 仅校验表达式语法（不求值）。
     *
     * @param condition 条件表达式文本
     * @return 语法合法且未超长返回 {@code true}
     */
    public static boolean isValid(String condition) {
        if (condition == null || condition.isBlank() || condition.length() > MAX_EXPRESSION_LENGTH) {
            return false;
        }
        try {
            PARSER.parseExpression(condition);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean accept(GuardContext context, ProcessModelFlow flow) {
        Map<String, Object> variables = context == null || context.getVariables() == null
                ? Map.of() : context.getVariables();
        SimpleEvaluationContext evaluationContext = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .build();
        variables.forEach(evaluationContext::setVariable);
        try {
            Boolean matched = expression.getValue(evaluationContext, variables, Boolean.class);
            return Boolean.TRUE.equals(matched);
        } catch (Exception e) {
            throw new ProcessModelException(ValidationCode.CONDITION_EVALUATION_FAILED,
                    "流转条件求值失败：" + source, flow == null ? null : flow.getKey());
        }
    }
}
