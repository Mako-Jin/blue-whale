package com.bluewhale.crm.integration.infrastructure.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.customer.domain.model.CustomerOpportunitySummary;
import com.bluewhale.crm.customer.domain.port.CustomerOpportunityPort;
import com.bluewhale.crm.integration.infrastructure.persistence.entity.OpportunityEntity;
import com.bluewhale.crm.integration.infrastructure.persistence.mapper.OpportunityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 客户商机摘要端口实现：只读投影 {@code crm_opportunity}。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CustomerOpportunityAdapter implements CustomerOpportunityPort {

    /** 360 中展示的商机明细上限 */
    private static final int ITEM_LIMIT = 5;

    private final OpportunityMapper opportunityMapper;

    @Override
    public CustomerOpportunitySummary summarize(Long customerId) {
        List<OpportunityEntity> entities = opportunityMapper.selectList(
                new LambdaQueryWrapper<OpportunityEntity>()
                        .eq(OpportunityEntity::getCustomerId, customerId));
        CustomerOpportunitySummary summary = new CustomerOpportunitySummary();
        summary.setTotalCount(entities.size());
        summary.setTotalAmount(entities.stream()
                .map(OpportunityEntity::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setItems(entities.stream()
                .sorted(Comparator.comparing(OpportunityEntity::getAmount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ITEM_LIMIT)
                .map(this::toItem)
                .toList());
        return summary;
    }

    /**
     * 实体转摘要项。
     *
     * @param entity 商机实体
     * @return 摘要项
     */
    private CustomerOpportunitySummary.Item toItem(OpportunityEntity entity) {
        CustomerOpportunitySummary.Item item = new CustomerOpportunitySummary.Item();
        item.setId(entity.getId());
        item.setName(entity.getName());
        item.setStage(entity.getStage());
        item.setAmount(entity.getAmount());
        return item;
    }
}
