package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * MES 员工技能矩阵实体，对应表 {@code mes_wf_skill_matrix}。
 *
 * <p>同一租户下员工与技能编码唯一，认证与失效日期用于技能有效性判定。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_skill_matrix")
public class MesSkillMatrix extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 技能编码 */
    private String skillCode;

    /** 技能名称 */
    private String skillName;

    /** 技能等级：JUNIOR/MIDDLE/SENIOR/EXPERT */
    private String skillLevel;

    /** 认证日期 */
    private LocalDate certifiedAt;

    /** 失效日期 */
    private LocalDate expiryAt;

    /** 状态：ENABLED/EXPIRED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
