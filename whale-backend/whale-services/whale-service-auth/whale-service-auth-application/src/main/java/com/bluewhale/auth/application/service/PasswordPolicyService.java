package com.bluewhale.auth.application.service;

/**
 * 口令策略服务（REQ-SEC-021 / REQ-SEC-023）：统一强度校验、历史复用校验、加密与历史记录。
 *
 * <p>四条口令入口（创建用户 / 更新用户 / 自助改密 / 管理员重置）都经由本服务，
 * 避免策略在接口层分散实现导致口径漂移。</p>
 *
 * @author BlueWhale
 */
public interface PasswordPolicyService {

    /**
     * 校验明文口令强度
     *
     * @param rawPassword 明文口令
     * @param username 用户名（口令不得包含）
     */
    void validate(String rawPassword, String username);

    /**
     * 校验口令未被近期使用（当前口令 + 最近 N 条历史）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param currentEncodedPassword 当前口令密文
     * @param rawPassword 待校验的新口令明文
     */
    void ensureNotReused(Long tenantId, Long userId, String currentEncodedPassword, String rawPassword);

    /**
     * 加密明文口令（调用前应先通过 {@link #validate}）
     *
     * @param rawPassword 明文口令
     * @return BCrypt 密文
     */
    String encode(String rawPassword);

    /**
     * 记录口令历史并按策略裁剪
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param encodedPassword 新口令密文
     */
    void recordHistory(Long tenantId, Long userId, String encodedPassword);

    /**
     * 是否要求新建/重置口令后强制目标用户下次登录改密
     *
     * @return 需要强制改密返回 true
     */
    boolean isForceChangeOnCreate();

    /**
     * 口令有效期（天），供登录到期判定
     *
     * @return 有效期天数；小于等于 0 表示不强制到期
     */
    int maxAgeDays();

    /**
     * 口令最小长度（供临时口令生成器保证系统生成的临时口令同样满足策略）
     *
     * @return 最小长度
     */
    int minLength();

    /**
     * 口令到期前提醒天数
     *
     * @return 提前提醒天数
     */
    int warnDays();
}
