package com.bluewhale.auth.application.model.importexport;

/**
 * 用户导出行（权限中心扩展 M4）：聚合快照 → Excel 行载荷，表头与导入模板一致。
 *
 * @param username 用户名
 * @param nickname 昵称
 * @param phone    手机号
 * @param email    邮箱
 * @param deptId   部门 ID
 * @param roleIds  角色 ID（逗号分隔）
 * @param status   状态（1 启用 / 0 停用）
 * @author BlueWhale
 */
public record UserExportRow(String username, String nickname, String phone, String email,
                            Long deptId, String roleIds, Integer status) {
}
