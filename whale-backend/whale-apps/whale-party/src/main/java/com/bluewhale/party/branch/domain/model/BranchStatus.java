package com.bluewhale.party.branch.domain.model;

/**
 * 支部状态。
 *
 * <p>只两态：在册与停用。停用是「这个支部不再接收党员、不再新建组织生活」，
 * 台账数据一律保留，不做删除——支部撤销在党组织口径里是历史事实，
 * 删掉会让历史组织生活记录失去归属。</p>
 *
 * @author BlueWhale
 */
public enum BranchStatus {

    /** 在册（可接收党员、可建组织生活） */
    ACTIVE("在册"),

    /** 停用（不接收党员、不可建组织生活，历史记录保留） */
    SUSPENDED("停用");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    BranchStatus(String label) {
        this.label = label;
    }

    /**
     * 查询中文标签。
     *
     * @return 中文标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 是否在册。
     *
     * @return 在册返回 true
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 解析状态码；未知值与空值一律按停用处理（失败关闭）。
     *
     * @param code 状态码
     * @return 状态
     */
    public static BranchStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return SUSPENDED;
        }
        for (BranchStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return SUSPENDED;
    }
}
