package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 计数对账结果。
 *
 * @author BlueWhale
 */
@Data
public class ReconcileReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次号（与 {@code fm_count_reconcile.batch_no} 一致，便于按次追溯） */
    private String batchNo;

    /** 本次核对的帖子批大小 */
    private Integer batchSize;

    /** 发现的差异条目数 */
    private Integer diffCount;

    /** 实际修正的条目数（dryRun 时恒为 0） */
    private Integer fixedCount;

    /** 是否只报告不修正 */
    private Boolean dryRun;

    /** 差异明细（最多回传 20 条，避免响应体过大） */
    private List<ReconcileItemVO> items;

    /** 明细是否被截断 */
    private Boolean truncated;
}
