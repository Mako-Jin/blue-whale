package com.bluewhale.auth.domain.repository;

import java.util.List;

/**
 * 口令历史仓储端口（REQ-SEC-023）：记录最近 N 次 BCrypt 密文，用于禁止口令复用。
 *
 * <p>只存密文、不存明文；保留条数由调用方按策略传入，超出部分由 {@link #trim} 清理。</p>
 *
 * <p><b>租户参数说明</b>：本端口刻意<b>显式传入 {@code tenantId}</b>（而非像其他仓储那样隐含租户
 * 由上下文提供）——口令历史是安全审计数据，显式传租户可避免上下文缺失或串号时误读写他人历史。</p>
 *
 * @author BlueWhale
 */
public interface PasswordHistoryRepository {

    /**
     * 查询用户最近的口令密文（按时间倒序）
     *
     * @param tenantId 租户 ID（显式传入，安全审计数据不走上下文隐含租户）
     * @param userId 用户 ID
     * @param limit 最多返回条数
     * @return 密文列表（无记录时为空列表）
     */
    List<String> listRecent(Long tenantId, Long userId, int limit);

    /**
     * 追加一条口令历史
     *
     * @param tenantId 租户 ID（显式传入，安全审计数据不走上下文隐含租户）
     * @param userId 用户 ID
     * @param encodedPassword BCrypt 密文
     */
    void append(Long tenantId, Long userId, String encodedPassword);

    /**
     * 仅保留最近 N 条历史，其余删除
     *
     * @param tenantId 租户 ID（显式传入，安全审计数据不走上下文隐含租户）
     * @param userId 用户 ID
     * @param keep 保留条数（小于 0 时按 0 处理）
     */
    void trim(Long tenantId, Long userId, int keep);
}
