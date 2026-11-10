package com.bluewhale.oa.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.analytics.application.query.ReportQuery;
import com.bluewhale.oa.analytics.application.view.ReportViews;

import java.util.List;

/**
 * 数据报表应用服务。
 *
 * @author BlueWhale
 */
public interface ReportAppService {

    /**
     * 报表目录（编码、名称、说明与列定义）。
     *
     * @return 目录项
     */
    List<ReportViews.CatalogItem> catalog();

    /**
     * 按条件运行报表（只读聚合，不落库）。
     *
     * @param code 报表编码
     * @param query 查询条件
     * @return 报表结果
     */
    ReportViews.ReportResult run(String code, ReportQuery query);

    /**
     * 生成指定月份的报表快照（同期间重复生成覆盖旧快照）。
     *
     * @param code 报表编码
     * @param period 统计期间（YYYY-MM，缺省为本月）
     * @param operatorId 操作人用户ID
     * @param operator 操作人姓名
     * @return 快照元信息
     */
    ReportViews.SnapshotView createSnapshot(String code, String period, Long operatorId, String operator);

    /**
     * 快照分页。
     *
     * @param code 报表编码（可为空）
     * @param current 页码
     * @param size 每页条数
     * @return 快照分页
     */
    PageResult<ReportViews.SnapshotView> pageSnapshots(String code, long current, long size);

    /**
     * 读取快照内容。
     *
     * @param id 快照ID
     * @return 快照对应的报表结果
     */
    ReportViews.ReportResult snapshotDetail(Long id);

    /**
     * 导出报表为 CSV，并登记导出记录。
     *
     * @param code 报表编码
     * @param query 查询条件
     * @param operatorId 操作人用户ID
     * @param operator 操作人姓名
     * @return 导出文件
     */
    ReportViews.ExportFile export(String code, ReportQuery query, Long operatorId, String operator);
}
