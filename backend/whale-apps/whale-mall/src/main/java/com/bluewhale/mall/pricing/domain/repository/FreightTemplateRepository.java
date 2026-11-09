package com.bluewhale.mall.pricing.domain.repository;

import com.bluewhale.mall.pricing.domain.model.FreightRegionRate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateQuery;

import java.util.List;
import java.util.Optional;

/**
 * 运费模板仓储。
 *
 * @author BlueWhale
 */
public interface FreightTemplateRepository {

    /**
     * 分页查询运费模板。
     *
     * @param query  查询条件
     * @param offset 偏移量
     * @param size   条数
     * @return 模板列表
     */
    List<FreightTemplate> page(FreightTemplateQuery query, long offset, int size);

    /**
     * 统计运费模板数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(FreightTemplateQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 模板
     */
    Optional<FreightTemplate> findById(Long id);

    /**
     * 名称是否已存在。
     *
     * @param templateName 模板名称
     * @param excludeId    排除的主键（修改时排除自身）
     * @return 已存在返回 true
     */
    boolean existsName(String templateName, Long excludeId);

    /**
     * 新增或更新。
     *
     * @param template 模板
     * @return 主键
     */
    Long save(FreightTemplate template);

    /**
     * 更新状态。
     *
     * @param id     主键
     * @param status 状态
     */
    void updateStatus(Long id, String status);

    /**
     * 查询模板的区域费率（仅 REGIONAL 用）。
     *
     * @param templateId 模板主键
     * @return 区域费率列表（无则空列表）
     */
    List<FreightRegionRate> regionRates(Long templateId);

    /**
     * 整体替换模板的区域费率（先删后插，保存模板时调用）。
     *
     * @param templateId 模板主键
     * @param rates      区域费率列表（REGIONAL 时为模板新费率；其他类型传空表）
     */
    void replaceRegionRates(Long templateId, List<FreightRegionRate> rates);
}
