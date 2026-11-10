package com.bluewhale.pdm.change.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 变更通知分页查询请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgNoticeQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;

    /** 通知编号 */
    private String noticeNo;

    /** 状态 */
    private String status;

    /** 来源变更单ID */
    private Long ecoId;
}
