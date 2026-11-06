package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 零件合并结果
 *
 * @author BlueWhale
 */
@Data
public class PdmPartMergeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源零件ID（已停用） */
    private Long sourceId;

    /** 保留零件ID */
    private Long targetId;

    /** 直接改写子件引用的草稿 BOM 行数 */
    private Integer rewrittenLines;

    /** 与保留件同父件重复、已累加用量并删除的草稿行数 */
    private Integer mergedLines;

    /** 因 BOM 非草稿而跳过、保持原样的行数 */
    private Integer skippedReleasedLines;
}
