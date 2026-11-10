package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公文文号规则。
 *
 * @author BlueWhale
 */
@Data
public class DocNoRule {

    /** 主键ID */
    private Long id;

    /** 规则编码 */
    private String code;

    /** 规则名称 */
    private String name;

    /** 适用范围：OUTGOING / INCOMING */
    private String docType;

    /** 文号前缀 */
    private String prefix;

    /** 期间格式：YEAR / MONTH / NONE */
    private String periodFormat;

    /** 流水位数（3–6） */
    private Integer serialLength;

    /** 分隔符 */
    private String separator;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
