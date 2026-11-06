package com.bluewhale.lms.training.domain.model;

/**
 * 必修分配范围。
 *
 * <p>本轮只把范围作为**追溯标记**落库：具体名单由调用方（前端组织树选择）解析后
 * 随请求提交，服务端负责批量落库与幂等。M5 接入 ERP 员工目录后再按
 * {@code scopeType + scopeValue} 做增量补人，避免现在臆造组织架构来源。</p>
 *
 * @author BlueWhale
 */
public enum AssignmentScope {

    /** 全员 */
    ALL,

    /** 按部门（scopeValue = 部门编码） */
    DEPT,

    /** 按角色（scopeValue = 角色编码） */
    ROLE,

    /** 指定人员 */
    USER;

    /**
     * 解析范围，未知取值回退指定人员。
     *
     * @param raw 原始取值
     * @return 分配范围
     */
    public static AssignmentScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        for (AssignmentScope scope : values()) {
            if (scope.name().equalsIgnoreCase(raw.trim())) {
                return scope;
            }
        }
        return USER;
    }
}
