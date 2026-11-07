package com.bluewhale.auth.application.user;

/**
 * 人员生命周期执行结果。
 *
 * <p>仅入职建号时返回一次性临时口令；其他动作该字段为空。临时口令不落库，
 * 调用方应在本次响应后立即提示用户首次登录改密。</p>
 *
 * @param userId 用户 ID
 * @param username 登录账号
 * @param status 账号状态（1 启用 / 0 停用）
 * @param deptId 部门 ID
 * @param temporaryPassword 一次性临时口令（仅入职建号时返回）
 * @author BlueWhale
 */
public record UserLifecycleVO(Long userId, String username, Integer status, Long deptId,
                              String temporaryPassword) {
}
