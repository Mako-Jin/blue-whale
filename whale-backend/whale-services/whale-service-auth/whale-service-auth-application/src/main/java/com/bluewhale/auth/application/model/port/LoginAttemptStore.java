package com.bluewhale.auth.application.model.port;

/**
 * 登录失败计数端口（Wave 6.27）
 *
 * <p>按（租户, 用户名）维度记录连续凭据校验失败次数：登录与二次认证（step-up）共用同一计数，
 * 避免「拿到会话后用 /auth/v1/reauth 继续爆破口令」的绕过路径。</p>
 *
 * <p>计数窗口由实现决定（默认 15 分钟，到达上限即锁定至窗口结束，到期自动解锁，无需人工干预）。</p>
 *
 * @author BlueWhale
 */
public interface LoginAttemptStore {

    /**
     * 查询剩余锁定秒数
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 已锁定时返回剩余秒数（&gt; 0），未锁定或计数不足时返回 0
     */
    long lockRemainingSeconds(Long tenantId, String username);

    /**
     * 记录一次凭据校验失败
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 当前窗口内的累计失败次数（存储不可用时返回 0）
     */
    int recordFailure(Long tenantId, String username);

    /**
     * 清除失败计数（凭据校验通过时调用）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     */
    void clear(Long tenantId, String username);
}
