package com.bluewhale.pdm.document.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 图文档分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmDocDocumentQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 所属目录（含子目录时由服务展开） */
    private Long folderId;

    /** 文档类型 */
    private String docType;

    /** 文档状态 */
    private String status;

    /** 零件号（模糊匹配） */
    private String partNo;

    /** 设计人 */
    private Long ownerId;

    /** 关键字：文档编号/标题/零件号/标签 */
    private String keyword;

    /** 创建日期起 */
    private LocalDate beginDate;

    /** 创建日期止 */
    private LocalDate endDate;
}
