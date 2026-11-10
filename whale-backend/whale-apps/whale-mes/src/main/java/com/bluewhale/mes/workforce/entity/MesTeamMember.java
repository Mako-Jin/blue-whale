package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * MES 班组成员实体，对应表 {@code mes_wf_team_member}。
 *
 * <p>同一租户下班组与员工唯一，主操作员标记用于排班主操作约束。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_team_member")
public class MesTeamMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码快照 */
    private String teamCode;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 是否主操作员：0 否 / 1 是 */
    private Integer primaryOperator;

    /** 技能等级：JUNIOR/MIDDLE/SENIOR/EXPERT */
    private String skillLevel;

    /** 入组日期 */
    private LocalDate joinDate;

    /** 离组日期 */
    private LocalDate leaveDate;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
