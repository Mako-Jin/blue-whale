package com.bluewhale.agent.support.ops;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 平台写操作目录：白名单式登记可被智能体发起、且必须人工确认的操作。
 *
 * <p>只登记低风险、参数可枚举的写操作；未登记的操作一律拒绝，避免模型自由拼装
 * 未知写请求。下游真实执行由 {@link com.bluewhale.agent.service.PlatformOpService} 完成。</p>
 *
 * @author BlueWhale
 */
public final class PlatformOperationCatalog {

    /** 表单域操作权限码 */
    public static final String PERMISSION_FORM = "sys:ai:ops:form";

    /** 操作目录（仅登记白名单操作） */
    private static final Map<String, Operation> OPERATIONS = buildOperations();

    /** 工具类不允许实例化 */
    private PlatformOperationCatalog() {
    }

    /**
     * 按名称查找操作。
     *
     * @param name 操作名，如 {@code form.definition.create}
     * @return 操作定义；未登记返回 {@code null}
     */
    public static Operation find(String name) {
        return StringUtils.hasText(name) ? OPERATIONS.get(name.trim()) : null;
    }

    /**
     * 按名称查找操作，未登记时抛出业务异常。
     *
     * @param name 操作名
     * @return 操作定义
     */
    public static Operation require(String name) {
        Operation operation = find(name);
        if (operation == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "agent.ops.operation-not-registered",
                    "未登记的平台操作：" + name, name);
        }
        return operation;
    }

    /**
     * 校验必填参数并返回下游请求体（仅保留白名单字段）。
     *
     * @param operation 操作定义
     * @param params    模型传入参数
     * @return 过滤后的请求体
     */
    public static Map<String, Object> normalizeParams(Operation operation, Map<String, Object> params) {
        Map<String, Object> source = params == null ? Map.of() : params;
        for (String key : operation.requiredParams()) {
            Object value = source.get(key);
            if (!(value instanceof String text) || !StringUtils.hasText(text)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "agent.ops.required-param-missing",
                        "缺少必填参数：" + key, key);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : operation.allowedParams()) {
            Object value = source.get(key);
            if (value != null) {
                body.put(key, value);
            }
        }
        return body;
    }

    /**
     * 构建操作定义：表单编码/名称必填，备注（{@code remark}，与 whale-form 字段同名）可选。
     *
     * @return 操作目录
     */
    private static Map<String, Operation> buildOperations() {
        Operation createForm = new Operation(
                "form.definition.create",
                "form",
                PERMISSION_FORM,
                "form",
                "POST",
                "/form/v1/definition",
                List.of("code", "name"),
                List.of("code", "name", "remark"),
                "创建表单定义",
                params -> "创建表单定义：" + text(params, "name") + "（" + text(params, "code") + "）");
        Map<String, Operation> operations = new LinkedHashMap<>();
        operations.put(createForm.name(), createForm);
        return Map.copyOf(operations);
    }

    /**
     * 读取文本参数（缺失返回空串）。
     *
     * @param params 参数
     * @param key    键
     * @return 文本值
     */
    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 单个平台操作定义。
     *
     * @param name           操作名（全局唯一）
     * @param domain         业务域（如 form）
     * @param permission     执行所需权限码
     * @param target         下游目标服务键（对应 {@code whale.agent.ops.targets.*}）
     * @param method         HTTP 方法
     * @param path           下游相对路径
     * @param requiredParams 必填参数
     * @param allowedParams  允许透传的参数
     * @param label          操作中文名
     * @param summarizer     参数摘要生成器（用于确认卡片）
     */
    public record Operation(String name, String domain, String permission, String target, String method, String path,
                            List<String> requiredParams, List<String> allowedParams, String label,
                            Function<Map<String, Object>, String> summarizer) {

        /**
         * 生成确认卡片展示的参数摘要。
         *
         * @param params 参数
         * @return 摘要文本
         */
        public String summary(Map<String, Object> params) {
            return summarizer.apply(params == null ? Map.of() : params);
        }
    }
}
