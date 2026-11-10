package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 满意度问卷查询条件。
 *
 * @author BlueWhale
 */
@Data
public class SurveyQuery {

    /** 当前页 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 工单 ID */
    private Long ticketId;

    /** 问卷状态 */
    private String status;
}
