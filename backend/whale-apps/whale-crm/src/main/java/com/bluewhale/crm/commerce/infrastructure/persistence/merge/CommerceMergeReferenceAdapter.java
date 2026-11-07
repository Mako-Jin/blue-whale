package com.bluewhale.crm.commerce.infrastructure.persistence.merge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.CollectionPlanEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.ContractLedgerEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.InvoiceRequestEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.CollectionPlanMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.ContractLedgerMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.InvoiceRequestMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.QuoteMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.SalesOrderMapper;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商务上下文客户合并引用适配器（M1-4）。
 *
 * <p>覆盖 {@code crm_quote / crm_contract / crm_sales_order / crm_collection_plan /
 * crm_invoice_request} 的 {@code customer_id} 外键，只更新，不删不建。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CommerceMergeReferenceAdapter implements MergeReferencePort {

    /** 报价 */
    private static final String QUOTE = "报价";

    /** 合同 */
    private static final String CONTRACT = "合同";

    /** 订单 */
    private static final String ORDER = "订单";

    /** 回款 */
    private static final String COLLECTION = "回款";

    /** 开票 */
    private static final String INVOICE = "开票";

    private final QuoteMapper quoteMapper;

    private final ContractLedgerMapper contractMapper;

    private final SalesOrderMapper orderMapper;

    private final CollectionPlanMapper collectionMapper;

    private final InvoiceRequestMapper invoiceMapper;

    @Override
    public Map<String, Long> countReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(QUOTE, quoteMapper.selectCount(
                new LambdaQueryWrapper<QuoteEntity>().eq(QuoteEntity::getCustomerId, sourceId)));
        counts.put(CONTRACT, contractMapper.selectCount(
                new LambdaQueryWrapper<ContractLedgerEntity>()
                        .eq(ContractLedgerEntity::getCustomerId, sourceId)));
        counts.put(ORDER, orderMapper.selectCount(
                new LambdaQueryWrapper<SalesOrderEntity>().eq(SalesOrderEntity::getCustomerId, sourceId)));
        counts.put(COLLECTION, collectionMapper.selectCount(
                new LambdaQueryWrapper<CollectionPlanEntity>()
                        .eq(CollectionPlanEntity::getCustomerId, sourceId)));
        counts.put(INVOICE, invoiceMapper.selectCount(
                new LambdaQueryWrapper<InvoiceRequestEntity>()
                        .eq(InvoiceRequestEntity::getCustomerId, sourceId)));
        return counts;
    }

    @Override
    public Map<String, Long> rewriteReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(QUOTE, update(quoteMapper, sourceId, targetId));
        counts.put(CONTRACT, update(contractMapper, sourceId, targetId));
        counts.put(ORDER, update(orderMapper, sourceId, targetId));
        counts.put(COLLECTION, update(collectionMapper, sourceId, targetId));
        counts.put(INVOICE, update(invoiceMapper, sourceId, targetId));
        return counts;
    }

    /**
     * 批量把 customer_id 从源客户更新为目标客户。
     *
     * @param mapper MyBatis-Plus Mapper
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 更新行数
     */
    private <T> long update(BaseMapper<T> mapper, Long sourceId, Long targetId) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.eq("customer_id", sourceId).set("customer_id", targetId);
        return mapper.update(null, wrapper);
    }
}
