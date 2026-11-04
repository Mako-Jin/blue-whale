package com.bluewhale.auth.application.dept.command;

/**
 * 部门新增/更新命令（AUTH-17 / T-DDD-9）
 *
 * <p>承载部门写用例的入参，空值默认值由 {@code Dept} 聚合负责，应用层不做兜底。</p>
 *
 * @param parentId 父部门 ID，为空时按 0（顶级）处理
 * @param deptName 部门名称
 * @param sort 排序号，为空时按 0 处理
 * @param leader 负责人
 * @param leaderUserId 负责人用户 ID（允许为空；{@code null} 时更新不修改原值）
 * @author BlueWhale
 */
public record SaveDeptCommand(Long parentId, String deptName, Integer sort, String leader, Long leaderUserId) {

    /**
     * 兼容重载：不含负责人用户 ID 的命令（{@code leaderUserId} 取 {@code null}）
     *
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     */
    public SaveDeptCommand(Long parentId, String deptName, Integer sort, String leader) {
        this(parentId, deptName, sort, leader, null);
    }
}
