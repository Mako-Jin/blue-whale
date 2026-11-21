package com.bluewhale.pdm.overview;

import java.util.List;

/**
 * 将作坊（PDM）的一次性统计快照。
 *
 * <p><b>为什么单独一组计数，而不是让概览 provider 直接查 Mapper。</b>
 * 概览 provider 属于 3D 城的取数层，一旦它开始自己写查询条件，
 * "未关闭变更单"的判定口径就有两份（2D 变更页按状态逐单看，3D 屏风自己数），
 * 迟早出现"后台说 3 张待批、城里说 5 张"。这里把所有口径收在
 * {@link PdmOverviewStatsService} 一处，provider 只负责排版。</p>
 *
 * <p><b>四个域各取各的账。</b>物料（pdm_product）、BOM（pdm_bom）、
 * 变更（pdm_chg_order）、图纸（pdm_doc_document）是 PDM 的四本账，
 * 屏风上一张牌对应一本账；评审（pdm_clb_review）不单列——
 * 设计评审有结论后都落在变更与图纸版本上，只数"开着没关"的评审意义不大。</p>
 *
 * @param partEnabled     在册物料数（status = ENABLED）
 * @param partDisabled    停用物料数
 * @param partByType      在册物料按类型计数（成品/部件/零件/原材料，只含在册）
 * @param bomReleased     生效中的 BOM 版本数（status = RELEASED）
 * @param bomDraft        草稿 BOM 数（status = DRAFT）
 * @param bomTotal        BOM 总数（含 OBSOLETE 废弃版）
 * @param chgOpen         未关闭变更单数（未进 COMPLETED / CLOSED / REJECTED / CANCELLED）
 * @param chgAssessing    卡在评估或审批的变更单数（ASSESSING + APPROVING）
 * @param chgExecuting    执行中的变更单数（已批准、正在改 BOM / 图纸）
 * @param docReleased     已发布图纸文档数（status = RELEASED）
 * @param docDraft        草稿文档数（status = DRAFT）
 * @param docCheckedOut   借出未还的文档数（checkedOutBy 非空）
 * @author BlueWhale
 */
public record PdmOverviewStats(
        long partEnabled,
        long partDisabled,
        List<TypeCount> partByType,
        long bomReleased,
        long bomDraft,
        long bomTotal,
        long chgOpen,
        long chgAssessing,
        long chgExecuting,
        long docReleased,
        long docDraft,
        long docCheckedOut) {

    /**
     * 物料类型计数：图表的横轴标签在服务端定死，屏风不再翻译码值。
     *
     * @param label 中文标签（成品 / 部件 / 零件 / 原材料）
     * @param count 在册物料数
     */
    public record TypeCount(String label, long count) {
    }
}
