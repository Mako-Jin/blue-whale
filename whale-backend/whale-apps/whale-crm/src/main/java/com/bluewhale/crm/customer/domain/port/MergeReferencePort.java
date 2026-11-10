package com.bluewhale.crm.customer.domain.port;

import java.util.Map;

/**
 * 客户合并引用端口（M1-4）。
 *
 * <p>客户上下文不直接访问其他上下文的持久化实现；各上下文在自身 infrastructure
 * 层实现本端口，提供「统计源客户被引用行数」与「将源客户引用重写为目标客户」两项能力。
 * Map 的 key 为上下文中文名（如 商机、报价、合同、订单、回款、开票、工单、退货、线索），
 * value 为对应引用行数。</p>
 *
 * @author BlueWhale
 */
public interface MergeReferencePort {

    /**
     * 统计源客户在各业务表中的引用行数。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID（用于实现方做幂等判断，可为 null）
     * @return 引用行数，key 为上下文中文名
     */
    Map<String, Long> countReferences(Long sourceId, Long targetId);

    /**
     * 将源客户的引用批量重写为目标客户（只更新，不删除、不新建）。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 实际更新行数，key 为上下文中文名
     */
    Map<String, Long> rewriteReferences(Long sourceId, Long targetId);
}
