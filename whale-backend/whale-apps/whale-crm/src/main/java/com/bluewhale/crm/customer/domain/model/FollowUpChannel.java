package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 跟进渠道。
 *
 * @author BlueWhale
 */
public enum FollowUpChannel {

    /** 电话 */
    CALL,

    /** 拜访 */
    VISIT,

    /** 邮件 */
    EMAIL,

    /** 即时通讯 */
    IM,

    /** 微信 */
    WECHAT,

    /** 其他 */
    OTHER;

    /**
     * 解析渠道码。
     *
     * @param code 渠道码
     * @return 渠道枚举
     */
    public static FollowUpChannel from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "跟进渠道不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "跟进渠道非法：" + code);
        }
    }
}
