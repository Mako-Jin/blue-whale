package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期类型视图。
 *
 * @author BlueWhale
 */
@Data
public class LeaveTypeView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 计量单位 */
    private String unit;

    /** 年度默认额度 */
    private BigDecimal annualQuota;

    /** 最小请假单位 */
    private BigDecimal minUnit;

    /** 是否必须上传附件 */
    private Boolean requiresAttachment;

    /** 是否允许负额度 */
    private Boolean allowNegative;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
