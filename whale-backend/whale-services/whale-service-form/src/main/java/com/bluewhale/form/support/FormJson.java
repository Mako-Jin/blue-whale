package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 表单 JSON 文本安全护栏。
 *
 * <p>所有进入 Jackson 解析的表单 JSON（结构定义、字段扩展配置、标签、提交数据、过滤条件）
 * 在解析前先做「嵌套深度」检查：深层嵌套（如 10000 层数组）会触发 Jackson 递归解析并可能栈溢出，
 * 属于典型的低成本 DoS 载荷。此处以一次线性扫描预先拦截，字符串字面量内的括号不计数。</p>
 *
 * <p><b>边界</b>：只限制嵌套深度，不替代长度限制（各入口已有 64KB / 4KB / 1KB 的长度上限）。</p>
 *
 * @author BlueWhale
 */
public final class FormJson {

    /** 允许的最大嵌套层级（对象/数组叠加计数） */
    public static final int MAX_DEPTH = 64;

    /** 工具类不允许实例化 */
    private FormJson() {
    }

    /**
     * 断言 JSON 文本未超过最大嵌套深度
     *
     * @param json JSON 文本（可空，空文本直接放行）
     * @param label 业务标签（用于错误提示）
     * @throws BizException 嵌套超过 {@link #MAX_DEPTH} 时抛出 {@code PARAM_ERROR}
     */
    public static void assertDepth(String json, String label) {
        if (json == null || json.isEmpty()) {
            return;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{' || current == '[') {
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                            label + " 的 JSON 嵌套层级不能超过 " + MAX_DEPTH);
                }
            } else if (current == '}' || current == ']') {
                depth = Math.max(depth - 1, 0);
            }
        }
    }
}
