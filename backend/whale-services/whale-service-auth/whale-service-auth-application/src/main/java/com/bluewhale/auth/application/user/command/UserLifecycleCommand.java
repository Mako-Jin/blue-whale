package com.bluewhale.auth.application.user.command;

/**
 * 人员生命周期命令：供 OA 人事异动审批通过后同步 Auth 账号。
 *
 * <p>动作取值：{@code ONBOARD} 入职建号/启用、{@code REGULAR} 转正、
 * {@code TRANSFER} 调岗、{@code LEAVE} 离职停用、{@code REHIRE} 返聘启用。
 * 仅内网接口使用，调用方须已完成审批与四眼校验。</p>
 *
 * @param action 生命周期动作
 * @param userId 目标用户 ID（入职未建号时为空）
 * @param username 登录账号（入职建号时必填）
 * @param nickname 昵称
 * @param deptId 目标部门 ID
 * @param phone 手机号
 * @param email 邮箱
 * @author BlueWhale
 */
public record UserLifecycleCommand(String action, Long userId, String username, String nickname,
                                   Long deptId, String phone, String email) {
}
