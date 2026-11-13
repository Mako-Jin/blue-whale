package com.bluewhale.auth.application.model.importexport;

/**
 * 用户导入行（权限中心扩展 M4）：Excel 一行 → 应用内导入载荷。
 *
 * <p>{@code deptId}/{@code roleIds}/{@code status} 保留单元格原文（String），
 * 由 {@code UserImportRowValidator} 统一做格式校验后，提交侧再解析为类型值——
 * 保证「解析错误也能定位到行/列」而不是在读取阶段直接抛异常中断全量预检。</p>
 *
 * @param rowNo    Excel 行号（1 起，表头为第 1 行，首个数据行为第 2 行）
 * @param username 用户名（必填）
 * @param nickname 昵称
 * @param phone    手机号
 * @param email    邮箱
 * @param deptId   部门 ID（单元格原文，可空）
 * @param roleIds  角色 ID（逗号分隔原文，可空）
 * @param status   状态（单元格原文：1 启用 / 0 停用，可空按启用处理）
 * @author BlueWhale
 */
public record UserImportRow(int rowNo, String username, String nickname, String phone, String email,
                            String deptId, String roleIds, String status) {
}
