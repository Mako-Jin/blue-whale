package com.bluewhale.auth.application.port;

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
     * 记录一次凭据校验失败，并基于同次写入的计数结果原子判定是否触达锁定上限
     *
     * <p>锁定判定使用本次自增后的计数（而非写入后另行回读），消除「计数与判定
     * 两次往返」之间的竞态：并发失败命中上限时不会漏判，期间发生的计数清除也不会误判。</p>
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 已触达上限时返回剩余锁定秒数（&gt; 0），未达上限返回 0；存储不可用时返回 0（fail-open）
     */
    long recordFailureAndCheckLocked(Long tenantId, String username);

    /**
     * 清除失败计数（凭据校验通过时调用）
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     */
    void clear(Long tenantId, String username);
}
