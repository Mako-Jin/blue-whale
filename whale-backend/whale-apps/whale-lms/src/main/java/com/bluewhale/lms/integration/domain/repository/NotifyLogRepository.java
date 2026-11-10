package com.bluewhale.lms.integration.domain.repository;

import com.bluewhale.lms.integration.domain.model.NotifyLog;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;

import java.util.List;
import java.util.Optional;

/**
 * 通知投递留痕仓储。
 *
 * @author BlueWhale
 */
public interface NotifyLogRepository {

    /**
     * 分页查询投递日志。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 日志列表
     */
    List<NotifyLog> page(NotifyLogQuery query, long offset, int size);

    /**
     * 统计投递日志总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(NotifyLogQuery query);

    /**
     * 按幂等键与接收人查询（判定是否已发）。
     *
     * @param idempotencyKey 幂等键
     * @param userId 接收人
     * @return 日志
     */
    Optional<NotifyLog> findByKeyAndUser(String idempotencyKey, Long userId);

    /**
     * 保存日志：无主键则插入，有主键则更新（失败重试覆盖为成功）。
     *
     * @param log 日志
     * @return 主键
     */
    Long save(NotifyLog log);

    /**
     * 统计指定幂等键下已成功的接收人数（汇报用）。
     *
     * @param idempotencyKey 幂等键
     * @return 成功条数
     */
    long countSentByKey(String idempotencyKey);
}
