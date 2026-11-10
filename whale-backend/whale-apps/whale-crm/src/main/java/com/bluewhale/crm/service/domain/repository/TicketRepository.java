package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketQuery;

import java.util.List;

/**
 * 工单仓储端口。
 *
 * @author BlueWhale
 */
public interface TicketRepository {

    /**
     * 分页查询工单。
     *
     * @param query 查询条件
     * @return 工单列表
     */
    List<Ticket> page(TicketQuery query);

    /**
     * 统计工单数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(TicketQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 工单；不存在返回 null
     */
    Ticket findById(Long id);

    /**
     * 新增工单。
     *
     * @param ticket 工单
     */
    void insert(Ticket ticket);

    /**
     * 更新工单。
     *
     * @param ticket 工单
     */
    void update(Ticket ticket);

    /**
     * 统计指定前缀的工单号数量。
     *
     * @param prefix 工单号前缀
     * @return 数量
     */
    long countByPrefix(String prefix);
}
