package com.bluewhale.pm.domain.service;

import com.bluewhale.pm.domain.model.StatusCategory;

import java.util.List;

/**
 * 默认工作项状态与流转模板（新建项目时复制为项目私有配置）。
 *
 * <p>模板以代码常量维护，不落库共享模板行，避免跨租户读取平台数据。</p>
 *
 * @author BlueWhale
 */
public final class DefaultWorkflowTemplate {

    /**
     * 模板状态定义
     *
     * @param code      状态编码
     * @param name      状态名称
     * @param category  状态分类
     * @param sortNo    排序号
     * @param isDefault 是否默认状态
     */
    public record StatusDefinition(String code, String name, String category, int sortNo, boolean isDefault) {
    }

    /**
     * 模板流转定义（按状态编码引用）
     *
     * @param fromCode 源状态编码
     * @param toCode   目标状态编码
     * @param name     流转动作名称
     */
    public record TransitionDefinition(String fromCode, String toCode, String name) {
    }

    /** 默认状态列表：待处理 / 处理中 / 已完成 */
    private static final List<StatusDefinition> STATUSES = List.of(
            new StatusDefinition("TODO", "待处理", StatusCategory.TODO.name(), 1, true),
            new StatusDefinition("IN_PROGRESS", "处理中", StatusCategory.IN_PROGRESS.name(), 2, false),
            new StatusDefinition("DONE", "已完成", StatusCategory.DONE.name(), 3, false));

    /** 默认流转列表：开始 / 完成 / 重新打开 / 退回 */
    private static final List<TransitionDefinition> TRANSITIONS = List.of(
            new TransitionDefinition("TODO", "IN_PROGRESS", "开始"),
            new TransitionDefinition("IN_PROGRESS", "DONE", "完成"),
            new TransitionDefinition("DONE", "IN_PROGRESS", "重新打开"),
            new TransitionDefinition("IN_PROGRESS", "TODO", "退回"));

    /**
     * 工具类不允许实例化
     */
    private DefaultWorkflowTemplate() {
    }

    /**
     * 获取默认状态定义。
     *
     * @return 状态定义列表（不可变）
     */
    public static List<StatusDefinition> statuses() {
        return STATUSES;
    }

    /**
     * 获取默认流转定义。
     *
     * @return 流转定义列表（不可变）
     */
    public static List<TransitionDefinition> transitions() {
        return TRANSITIONS;
    }
}
