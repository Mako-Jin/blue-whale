package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 试卷实体，对应表 {@code lr_paper}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_paper")
public class PaperEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 试卷编码（租户内唯一） */
    private String paperCode;

    /** 试卷名称 */
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 考试时长（分钟） */
    private Integer durationMinutes;

    /** 总分 */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 题目数 */
    private Integer questionCount;

    /** 抽题种子 */
    private String seed;

    /** 抽题规则 JSON 快照 */
    private String ruleConfig;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
