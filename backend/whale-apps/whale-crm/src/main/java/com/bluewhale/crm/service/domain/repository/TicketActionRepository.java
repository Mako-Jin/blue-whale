package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.TicketAction;

import java.util.List;

/**
 * 工单处理记录仓储端口。
 *
 * @author BlueWhale
 */
public interface TicketActionRepository {

    /**
     * 查询工单时间线。
     *
     * @param ticketId 工单 ID
     * @return 处理记录（按创建时间升序）
     */
    List<TicketAction> findByTicketId(Long ticketId);

    /**
     * 追加处理记录。
     *
     * @param action 处理记录
     */
    void insert(TicketAction action);
}
