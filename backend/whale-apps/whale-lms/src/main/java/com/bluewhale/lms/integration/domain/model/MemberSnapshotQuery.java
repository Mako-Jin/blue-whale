package com.bluewhale.lms.integration.domain.model;

import lombok.Data;

/**
 * 员工快照查询条件。
 *
 * @author BlueWhale
 */
@Data
public class MemberSnapshotQuery {

    /** 关键字（员工编号 / 姓名模糊） */
    private String keyword;

    /** 组织编码 */
    private String orgCode;

    /** 在职状态：ACTIVE / LEFT */
    private String status;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
