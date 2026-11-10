package com.bluewhale.crm.commerce.infrastructure.merge;

import com.bluewhale.crm.commerce.infrastructure.client.ContractRewriteClient;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 合同服务（whale-contract）客户合并引用适配器（M1-4）。
 *
 * <p>CRM 本地台账（{@code crm_contract.customer_id}）由
 * {@code CommerceMergeReferenceAdapter} 重写；本适配器补齐合同服务侧
 * {@code biz_type=CRM_CUSTOMER} 弱关联的重写，聚合 key 用「合同服务」避免与台账「合同」混计。
 * 受 {@code whale.crm.contract.enabled} 总开关控制，关闭时返回空映射（跳过）。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ContractServiceMergeReferenceAdapter implements MergeReferencePort {

    /** 聚合 key：合同服务（区别于本地台账「合同」） */
    private static final String CONTRACT_SERVICE = "合同服务";

    private final ContractRewriteClient rewriteClient;

    private final CrmContractProperties contractProperties;

    @Override
    public Map<String, Long> countReferences(Long sourceId, Long targetId) {
        if (!contractProperties.isEnabled()) {
            return Map.of();
        }
        return Map.of(CONTRACT_SERVICE, rewriteClient.countReferences(sourceId));
    }

    @Override
    public Map<String, Long> rewriteReferences(Long sourceId, Long targetId) {
        if (!contractProperties.isEnabled()) {
            return Map.of();
        }
        return Map.of(CONTRACT_SERVICE, rewriteClient.rewriteReferences(sourceId, targetId));
    }
}
