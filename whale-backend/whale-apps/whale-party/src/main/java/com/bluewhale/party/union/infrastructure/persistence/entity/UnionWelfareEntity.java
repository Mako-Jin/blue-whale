package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节日福利批次实体，对应表 {@code pty_union_welfare}。
 *
 * <p>{@code welfareYear} 显式映射到 {@code welfare_year}：默认驼峰转换在
 * MyBatis-Plus 多词属性上不可靠，显式声明可避免列名猜错导致查询漏字段。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_welfare")
public class UnionWelfareEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 批次编号（租户内唯一） */
    private String welfareNo;

    /** 福利名称 */
    private String title;

    /** 福利年度 */
    @TableField("welfare_year")
    private Integer welfareYear;

    /** 发放标准 */
    private BigDecimal standardAmount;

    /** 状态 */
    private String status;

    /** 发放时间 */
    private LocalDateTime issuedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;
}
