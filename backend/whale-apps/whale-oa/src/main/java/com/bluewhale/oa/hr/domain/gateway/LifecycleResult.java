package com.bluewhale.oa.hr.domain.gateway;

/**
 * Auth 生命周期执行结果。
 *
 * @param userId 用户ID（入职建号后回填）
 * @param status 账号状态
 * @param temporaryPassword 一次性临时口令（仅入职建号时返回）
 * @author BlueWhale
 */
public record LifecycleResult(Long userId, Integer status, String temporaryPassword) {
}
