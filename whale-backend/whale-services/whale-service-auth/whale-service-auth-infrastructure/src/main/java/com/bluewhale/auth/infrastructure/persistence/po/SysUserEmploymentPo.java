package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户工作经历子表实体（权限中心扩展 M3：对应 {@code sys_user_employment}，多条有序）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_employment")
public class SysUserEmploymentPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 公司名称 */
    private String company;
    /** 部门 */
    private String department;
    /** 职位 */
    private String position;
    /** 用工性质：FORMAL 正式 / CONTRACT 合同 / INTERN 实习 / OUTSOURCING 外包 */
    private String employmentType;
    /** 入职时间 */
    private LocalDate startDate;
    /** 离职时间（在职为 NULL） */
    private LocalDate endDate;
    /** 排序号（倒序展示） */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
