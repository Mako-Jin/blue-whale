package com.bluewhale.party.branch.domain.model;

import lombok.Data;

/**
 * 支部查询条件。
 *
 * @author BlueWhale
 */
@Data
public class BranchQuery {

    /** 关键字（编码或名称模糊） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 上级支部 ID（不传表示不限） */
    private Long parentId;

    /** 关联 ERP 组织编码 */
    private String orgCode;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
