package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公文模板。
 *
 * @author BlueWhale
 */
@Data
public class DocTemplate {

    /** 主键ID */
    private Long id;

    /** 模板编码 */
    private String code;

    /** 模板名称 */
    private String name;

    /** 适用范围：OUTGOING / INCOMING */
    private String docType;

    /** 版本号 */
    private Integer version;

    /** 状态：DRAFT / RELEASED / DISABLED */
    private String status;

    /** 红头文字 */
    private String headerText;

    /** 版记文字 */
    private String footerText;

    /** 正文模板 */
    private String bodyTemplate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
