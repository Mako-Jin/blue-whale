package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单条计数差异（对账结果明细）。
 *
 * @author BlueWhale
 */
@Data
public class ReconcileItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帖子ID */
    private Long threadId;

    /** 计数项：REPLY / LIKE / COLLECT */
    private String metric;

    /** 核对前展示值 */
    private Integer before;

    /** 明细重算的真实值 */
    private Integer actual;

    /** 是否已修正 */
    private Boolean fixed;
}
