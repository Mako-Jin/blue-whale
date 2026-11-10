package com.bluewhale.lms.training.domain.model;

/**
 * 签到方式。
 *
 * @author BlueWhale
 */
public enum CheckinType {

    /** 时间窗内自助签到 */
    SELF,

    /** 补签：必须记录操作人与备注，留痕可追溯 */
    SUPPLEMENT
}
