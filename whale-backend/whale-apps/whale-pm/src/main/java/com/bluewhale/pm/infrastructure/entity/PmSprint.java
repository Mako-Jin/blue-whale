package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 项目迭代实体，对应表 {@code pm_sprint}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_sprint")
public class PmSprint extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 迭代名称（项目内唯一） */
    private String name;

    /** 迭代目标 */
    private String goal;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态：FUTURE / ACTIVE / CLOSED */
    private String status;
}
