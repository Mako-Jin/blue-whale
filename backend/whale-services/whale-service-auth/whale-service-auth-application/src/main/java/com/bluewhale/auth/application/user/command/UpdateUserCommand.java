package com.bluewhale.auth.application.user.command;

/**
 * 更新用户命令：承载管理端「更新用户」用例的输入参数。
 *
 * <p>用户名不可修改；{@code rawPassword} 为空表示不修改口令，{@code status} 为空表示不修改状态。</p>
 *
 * @param rawPassword 新明文口令（可空；非空时经 BCrypt 加密后重置）
 * @param nickname 昵称
 * @param phone 手机号
 * @param email 邮箱
 * @param avatar 头像地址
 * @param deptId 所属部门 ID
 * @param status 状态（1 启用 / 0 停用），为空时按不变更处理
 * @author BlueWhale
 */
public record UpdateUserCommand(String rawPassword, String nickname, String phone, String email,
                                String avatar, Long deptId, Integer status) {
}
