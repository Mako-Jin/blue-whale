package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.PartnerReport;
import com.bluewhale.crm.marketing.domain.model.PartnerReportQuery;

import java.util.List;

/**
 * 渠道报单仓储。
 *
 * @author BlueWhale
 */
public interface PartnerReportRepository {

    /**
     * 分页查询渠道报单。
     *
     * @param query 查询条件
     * @return 报单列表
     */
    List<PartnerReport> page(PartnerReportQuery query);

    /**
     * 统计渠道报单数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PartnerReportQuery query);

    /**
     * 按主键查询报单。
     *
     * @param id 主键
     * @return 报单；不存在返回 null
     */
    PartnerReport findById(Long id);

    /**
     * 新增报单。
     *
     * @param report 报单
     */
    void insert(PartnerReport report);

    /**
     * 更新报单。
     *
     * @param report 报单
     */
    void update(PartnerReport report);

    /**
     * 统计指定前缀的报单号数量。
     *
     * @param prefix 前缀
     * @return 数量
     */
    long countByReportNoPrefix(String prefix);

    /**
     * 判断同一客户同一项目是否存在有效报单（PENDING / APPROVED）。
     *
     * @param customerId 客户 ID
     * @param projectName 项目名称
     * @param excludeId 排除的报单 ID
     * @return 存在返回 true
     */
    boolean existsActiveReport(Long customerId, String projectName, Long excludeId);
}
