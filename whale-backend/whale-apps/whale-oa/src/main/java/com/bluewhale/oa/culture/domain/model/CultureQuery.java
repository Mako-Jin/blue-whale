package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业文化分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CultureQuery extends PageQuery {

    /** 关键字（标题 / 内容 / 对象姓名） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 文化动态类型 */
    private String eventType;

    /** 祝福类型 */
    private String wishType;

    /** 祝福对象用户ID */
    private Long targetUserId;

    /** 是否仅看与我相关（建议按提交人过滤） */
    private Boolean mine;

    /** 建议类别 */
    private String category;
}
