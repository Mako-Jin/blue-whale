package com.bluewhale.crm.sales.infrastructure.persistence.merge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.LeadEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.SalesOpportunityEntity;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.LeadMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.mapper.SalesOpportunityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 销售上下文客户合并引用适配器（M1-4）。
 *
 * <p>仅处理带客户外键的表：{@code crm_opportunity.customer_id} 与
 * {@code crm_lead.customer_id}；商机阶段历史无客户列，跳过。只更新，不删不建。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class SalesMergeReferenceAdapter implements MergeReferencePort {

    /** 商机 */
    private static final String OPPORTUNITY = "商机";

    /** 线索 */
    private static final String LEAD = "线索";

    private final SalesOpportunityMapper opportunityMapper;

    private final LeadMapper leadMapper;

    @Override
    public Map<String, Long> countReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(OPPORTUNITY, opportunityMapper.selectCount(
                new LambdaQueryWrapper<SalesOpportunityEntity>()
                        .eq(SalesOpportunityEntity::getCustomerId, sourceId)));
        counts.put(LEAD, leadMapper.selectCount(
                new LambdaQueryWrapper<LeadEntity>().eq(LeadEntity::getCustomerId, sourceId)));
        return counts;
    }

    @Override
    public Map<String, Long> rewriteReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(OPPORTUNITY, (long) opportunityMapper.update(null,
                new LambdaUpdateWrapper<SalesOpportunityEntity>()
                        .eq(SalesOpportunityEntity::getCustomerId, sourceId)
                        .set(SalesOpportunityEntity::getCustomerId, targetId)));
        counts.put(LEAD, (long) leadMapper.update(null,
                new LambdaUpdateWrapper<LeadEntity>()
                        .eq(LeadEntity::getCustomerId, sourceId)
                        .set(LeadEntity::getCustomerId, targetId)));
        return counts;
    }
}
