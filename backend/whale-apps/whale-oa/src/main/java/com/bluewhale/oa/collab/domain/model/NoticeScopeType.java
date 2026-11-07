package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 公告发布范围。
 *
 * @author BlueWhale
 */
public enum NoticeScopeType {

    /** 全员 */
    ALL,

    /** 部门 */
    DEPT,

    /** 指定用户 */
    USER;

    /**
     * 解析发布范围。
     *
     * @param value 范围字符串
     * @return 发布范围
     */
    public static NoticeScopeType of(String value) {
        try {
            return NoticeScopeType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_INVALID, "公告范围非法：" + value);
        }
    }
}
