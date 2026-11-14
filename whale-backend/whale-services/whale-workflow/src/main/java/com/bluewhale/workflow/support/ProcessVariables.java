package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程变量序列化工具：{@code process_instance.variables_json} 的读写与合并。
 *
 * <p>变量随实例落库（而非跨服务查表单），保证条件选路可重放、可审计；审批时追加的变量
 * 覆盖同名键（合并语义）。空映射统一写为 {@code null}，保持旧行行为不变。</p>
 *
 * @author BlueWhale
 */
public final class ProcessVariables {

    /** 变量 JSON 长度上限（字符数）：64KB */
    public static final int MAX_JSON_LENGTH = 65536;

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessVariables.class);

    /** JSON 解析器（线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private ProcessVariables() {
    }

    /**
     * 序列化流程变量；空映射返回 {@code null}。
     *
     * @param variables 流程变量，可为 {@code null}
     * @return JSON 文本；无变量返回 {@code null}
     * @throws BizException 序列化失败或超长时抛出 {@code PARAM_ERROR}
     */
    public static String write(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(variables);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程变量不是合法的 JSON 对象");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "流程变量长度不能超过 " + MAX_JSON_LENGTH + " 字符");
        }
        return json;
    }

    /**
     * 反序列化流程变量；空文本返回空映射。
     *
     * @param variablesJson 变量 JSON 文本，可为 {@code null}
     * @return 流程变量映射（非法 JSON 容错为空映射）
     */
    public static Map<String, Object> read(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(variablesJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            LOG.warn("流程变量 JSON 解析失败，按空变量处理：length={}, error={}",
                    variablesJson.length(), e.getClass().getSimpleName());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 合并变量：已落库变量 + 本次追加变量（同名覆盖）。
     *
     * @param variablesJson 已落库变量 JSON，可为 {@code null}
     * @param incoming 本次追加变量，可为 {@code null}
     * @return 合并后的变量映射（非 {@code null}）
     */
    public static Map<String, Object> merge(String variablesJson, Map<String, Object> incoming) {
        Map<String, Object> merged = read(variablesJson);
        if (incoming != null && !incoming.isEmpty()) {
            merged.putAll(incoming);
        }
        return merged;
    }
}
