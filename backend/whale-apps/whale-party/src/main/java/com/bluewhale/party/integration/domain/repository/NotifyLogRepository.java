package com.bluewhale.party.integration.domain.repository;

import com.bluewhale.party.integration.domain.model.NotifyLog;
import com.bluewhale.party.integration.domain.model.NotifyLogQuery;

import java.util.List;
import java.util.Optional;

/**
 * 通知留痕仓储端口。
 *
 * @author BlueWhale
 */
public interface NotifyLogRepository {

    /**
     * 按幂等键与接收人查留痕。
     *
     * @param idempotencyKey 幂等键
     * @param userId 接收人
     * @return 留痕
     */
    Optional<NotifyLog> findByKeyAndUser(String idempotencyKey, Long userId);

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 留痕列表
     */
    List<NotifyLog> page(NotifyLogQuery query, long offset, int size);

    /**
     * 统计条数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(NotifyLogQuery query);

    /**
     * 保存留痕（存在则更新）。
     *
     * @param log 留痕
     * @return 主键
     */
    Long save(NotifyLog log);
}
