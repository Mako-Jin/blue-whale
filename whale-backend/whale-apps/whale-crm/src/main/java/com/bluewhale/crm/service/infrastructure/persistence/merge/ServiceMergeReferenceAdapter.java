package com.bluewhale.crm.service.infrastructure.persistence.merge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnRequestEntity;
import com.bluewhale.crm.service.infrastructure.persistence.entity.TicketEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.ReturnRequestMapper;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务上下文客户合并引用适配器（M1-4）。
 *
 * <p>覆盖 {@code crm_ticket.customer_id} 与 {@code crm_return_request.customer_id}，
 * 只更新，不删不建。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ServiceMergeReferenceAdapter implements MergeReferencePort {

    /** 工单 */
    private static final String TICKET = "工单";

    /** 退货 */
    private static final String RETURN = "退货";

    private final TicketMapper ticketMapper;

    private final ReturnRequestMapper returnRequestMapper;

    @Override
    public Map<String, Long> countReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TICKET, ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>().eq(TicketEntity::getCustomerId, sourceId)));
        counts.put(RETURN, returnRequestMapper.selectCount(
                new LambdaQueryWrapper<ReturnRequestEntity>()
                        .eq(ReturnRequestEntity::getCustomerId, sourceId)));
        return counts;
    }

    @Override
    public Map<String, Long> rewriteReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TICKET, (long) ticketMapper.update(null,
                new LambdaUpdateWrapper<TicketEntity>()
                        .eq(TicketEntity::getCustomerId, sourceId)
                        .set(TicketEntity::getCustomerId, targetId)));
        counts.put(RETURN, (long) returnRequestMapper.update(null,
                new LambdaUpdateWrapper<ReturnRequestEntity>()
                        .eq(ReturnRequestEntity::getCustomerId, sourceId)
                        .set(ReturnRequestEntity::getCustomerId, targetId)));
        return counts;
    }
}
