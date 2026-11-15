package com.bluewhale.form.support;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.po.FormDataHistoryPo;
import com.bluewhale.form.po.FormDataPo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 表单数据变更历史记录器。
 *
 * <p>留痕与业务写入处于同一事务（由调用方的事务边界保证），历史写入失败即抛出、数据变更随之回滚，
 * 避免出现「数据已改、历史缺失」的合规缺口。与旁路审计（失败仅告警）的取舍不同，这里把历史视为业务数据。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FormDataHistoryRecorder {

    /** 变更类型：提交 */
    public static final String CREATE = "CREATE";

    /** 变更类型：修改 */
    public static final String UPDATE = "UPDATE";

    /** 变更类型：删除 */
    public static final String DELETE = "DELETE";

    /** 变更类型：审批状态流转 */
    public static final String APPROVAL = "APPROVAL";

    /** 变更字段串最大长度（与列宽一致） */
    private static final int MAX_CHANGED_KEYS_LENGTH = 1024;

    /** JSON 解析器（仅读取，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormDataHistoryMapper formDataHistoryMapper;

    /**
     * 记录提交留痕
     *
     * @param entity 已落库的表单数据实体
     */
    public void recordCreate(FormDataPo entity) {
        FormDataHistoryPo history = base(entity, CREATE);
        history.setAfterJson(entity.getDataJson());
        history.setOperator(operatorOf(entity.getSubmitter()));
        formDataHistoryMapper.insert(history);
    }

    /**
     * 记录修改留痕（含变更前快照与变更字段集合）
     *
     * @param entity 已更新的表单数据实体
     * @param beforeJson 变更前的数据JSON
     */
    public void recordUpdate(FormDataPo entity, String beforeJson) {
        FormDataHistoryPo history = base(entity, UPDATE);
        history.setBeforeJson(beforeJson);
        history.setAfterJson(entity.getDataJson());
        history.setChangedKeys(changedKeys(beforeJson, entity.getDataJson()));
        history.setOperator(operatorOf(entity.getSubmitter()));
        formDataHistoryMapper.insert(history);
    }

    /**
     * 记录删除留痕（保留删除前快照）
     *
     * @param entity 删除前的表单数据实体
     */
    public void recordDelete(FormDataPo entity) {
        FormDataHistoryPo history = base(entity, DELETE);
        history.setBeforeJson(entity.getDataJson());
        history.setOperator(operatorOf(entity.getSubmitter()));
        formDataHistoryMapper.insert(history);
    }

    /**
     * 记录审批状态流转留痕（外部工作流驱动，仅记录状态与流程实例）
     *
     * @param entity 状态已变更的表单数据实体
     * @param beforeBizStatus 变更前业务状态
     * @param comment 审批意见（可为空）
     */
    public void recordApproval(FormDataPo entity, int beforeBizStatus, String comment) {
        FormDataHistoryPo history = base(entity, APPROVAL);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("bizStatus", beforeBizStatus);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("bizStatus", entity.getBizStatus());
        after.put("processInstanceId", entity.getProcessInstanceId());
        if (StringUtils.hasText(comment)) {
            after.put("comment", comment);
        }
        history.setBeforeJson(writeQuietly(before));
        history.setAfterJson(writeQuietly(after));
        history.setChangedKeys("bizStatus");
        history.setOperator(operatorOf(entity.getSubmitter()));
        formDataHistoryMapper.insert(history);
    }

    /**
     * 构造历史记录公共字段
     *
     * @param entity 表单数据实体
     * @param changeType 变更类型
     * @return 历史记录实体
     */
    private static FormDataHistoryPo base(FormDataPo entity, String changeType) {
        FormDataHistoryPo history = new FormDataHistoryPo();
        history.setTenantId(entity.getTenantId());
        history.setDataId(entity.getId());
        history.setDefinitionCode(entity.getDefinitionCode());
        history.setChangeType(changeType);
        history.setChangeTime(LocalDateTime.now());
        return history;
    }

    /**
     * 取操作人：优先当前请求上下文，回落提交人
     *
     * @param submitter 提交人账号
     * @return 操作人账号
     */
    private static String operatorOf(String submitter) {
        String username = RequestContextHolder.getUsername();
        return StringUtils.hasText(username) ? username : submitter;
    }

    /**
     * 计算变更字段集合（前后快照的并集逐字段比较），超长截断
     *
     * @param beforeJson 变更前 JSON
     * @param afterJson 变更后 JSON
     * @return 逗号分隔的字段编码；无变化或无法解析时返回 {@code null}
     */
    static String changedKeys(String beforeJson, String afterJson) {
        Map<String, Object> before = parseQuietly(beforeJson);
        Map<String, Object> after = parseQuietly(afterJson);
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                changed.add(key);
            }
        }
        if (changed.isEmpty()) {
            return null;
        }
        String joined = String.join(",", changed);
        return joined.length() > MAX_CHANGED_KEYS_LENGTH
                ? joined.substring(0, MAX_CHANGED_KEYS_LENGTH) : joined;
    }

    /**
     * 容错解析 JSON 对象（非法内容视为空映射）
     *
     * @param json JSON 文本
     * @return 字段值映射
     */
    private static Map<String, Object> parseQuietly(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> data = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return data == null ? new LinkedHashMap<>() : data;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 序列化状态快照（失败抛出非受检异常，由事务回滚兜底）
     *
     * @param snapshot 状态快照
     * @return JSON 文本
     */
    private static String writeQuietly(Map<String, Object> snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("审批留痕序列化失败", e);
        }
    }
}
