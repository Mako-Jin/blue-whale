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
 * @author BlueWhale
 */
public record SaveDeptCommand(Long parentId, String deptName, Integer sort, String leader) {
}
