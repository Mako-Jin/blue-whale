package com.bluewhale.oa.administrative.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行政业务通用分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdministrativeQuery extends PageQuery {

    /** 关键字（单号 / 车牌 / 印章 / 事由） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 申请人用户ID */
    private Long applicantId;

    /** 车辆ID */
    private Long vehicleId;

    /** ERP 推送状态 */
    private String erpPushStatus;
}
