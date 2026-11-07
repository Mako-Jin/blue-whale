package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.Partner;
import com.bluewhale.crm.marketing.domain.model.PartnerQuery;

import java.util.List;

/**
 * 渠道伙伴仓储。
 *
 * @author BlueWhale
 */
public interface PartnerRepository {

    /**
     * 分页查询渠道伙伴。
     *
     * @param query 查询条件
     * @return 伙伴列表
     */
    List<Partner> page(PartnerQuery query);

    /**
     * 统计渠道伙伴数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PartnerQuery query);

    /**
     * 按主键查询伙伴。
     *
     * @param id 主键
     * @return 伙伴；不存在返回 null
     */
    Partner findById(Long id);

    /**
     * 新增伙伴。
     *
     * @param partner 伙伴
     */
    void insert(Partner partner);

    /**
     * 更新伙伴。
     *
     * @param partner 伙伴
     */
    void update(Partner partner);

    /**
     * 判断伙伴编码是否已存在。
     *
     * @param partnerCode 伙伴编码
     * @param excludeId 排除的伙伴 ID（编辑时传当前 ID）
     * @return 已存在返回 true
     */
    boolean existsByCode(String partnerCode, Long excludeId);
}
