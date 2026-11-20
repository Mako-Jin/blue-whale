package com.bluewhale.workflow.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 审批人覆盖（{@code assigneeOverrides}）的解析、序列化与校验。
 *
 * <p>覆盖以 {@code nodeKey -> 账号列表} 表示，只在发起时校验一次并快照到
 * {@code process_instance.assignee_overrides_json}，后续节点推进直接复用，
 * 保证在途实例不受组织变更影响。</p>
 *
 * @author BlueWhale
 */
public final class AssigneeOverrides {

    /** 单节点覆盖人数上限 */
    public static final int MAX_PER_NODE = 20;

    /** 账号长度上限 */
    public static final int MAX_ACCOUNT_LENGTH = 64;

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(AssigneeOverrides.class);

    /** JSON 解析器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private AssigneeOverrides() {
    }

    /**
     * 解析快照 JSON；空值或格式非法返回空 Map（历史实例无快照，行为与静态审批人一致）。
     *
     * @param json 快照 JSON
     * @return 节点键 → 账号列表（非 {@code null}）
     */
    public static Map<String, List<String>> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, List<String>> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {
                    });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            LOG.warn("审批人覆盖快照解析失败，按空覆盖处理：length={}, error={}",
                    json.length(), ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    /**
     * 序列化快照；空覆盖返回 {@code null}（不写库）。
     *
     * @param overrides 节点键 → 账号列表
     * @return JSON 文本或 {@code null}
     */
    public static String write(Map<String, List<String>> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(overrides);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "workflow.assignee-override.serialize-failed",
                    "审批人覆盖序列化失败");
        }
    }

    /**
     * 校验覆盖是否合法。
     *
     * @param model 流程模型
     * @param overrides 节点键 → 账号列表
     */
    public static void validate(ProcessModel model, Map<String, List<String>> overrides) {
        normalize(model, overrides);
    }

    /**
     * 校验并归一化覆盖：去首尾空白、去重；空覆盖返回空 Map。
     *
     * @param model 流程模型
     * @param overrides 原始覆盖
     * @return 归一化后的覆盖（不可变）
     * @throws BizException 节点不存在、节点类型不允许、账号为空/超长/超量时抛出
     */
    public static Map<String, List<String>> normalize(ProcessModel model, Map<String, List<String>> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : overrides.entrySet()) {
            String key = resolveNodeKey(model, entry.getKey());
            result.put(key, normalizeAccounts(key, entry.getValue()));
        }
        return Map.copyOf(result);
    }

    /**
     * 校验节点存在且类型允许，返回归一化后的节点键。
     *
     * @param model 流程模型
     * @param rawKey 原始节点键
     * @return 去空白后的节点键
     */
    private static String resolveNodeKey(ProcessModel model, String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        ProcessModelNode node = model.node(key);
        if (node == null) {
            throw invalid("审批人覆盖包含未知节点：" + (key.isEmpty() ? "(空)" : key));
        }
        ProcessNodeType type = node.getType();
        if (type != ProcessNodeType.USER_TASK && type != ProcessNodeType.CC_TASK) {
            throw invalid("审批人覆盖只允许用户任务或抄送节点：" + key);
        }
        return key;
    }

    /**
     * 校验并归一化单节点账号列表：去首尾空白、去重。
     *
     * @param key 节点键
     * @param accounts 原始账号列表
     * @return 归一化后的不可变账号列表
     */
    private static List<String> normalizeAccounts(String key, List<String> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            throw invalid("审批人覆盖账号不能为空：" + key);
        }
        if (accounts.size() > MAX_PER_NODE) {
            throw invalid("审批人覆盖单节点最多 " + MAX_PER_NODE + " 人：" + key);
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String account : accounts) {
            String value = account == null ? "" : account.trim();
            if (value.isEmpty()) {
                throw invalid("审批人覆盖账号不能为空：" + key);
            }
            if (value.length() > MAX_ACCOUNT_LENGTH) {
                throw invalid("审批人覆盖账号长度不能超过 " + MAX_ACCOUNT_LENGTH + "：" + key);
            }
            cleaned.add(value);
        }
        return List.copyOf(cleaned);
    }

    /**
     * 构造覆盖校验异常（参数错误）。
     *
     * @param message 提示文案
     * @return 业务异常
     */
    private static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
