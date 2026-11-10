package com.bluewhale.party.branch.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 支部实体，对应表 {@code pty_branch}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_branch")
public class BranchEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 支部编码（租户内唯一） */
    private String branchCode;

    /** 支部名称 */
    private String branchName;

    /** 上级支部 ID，0 为一级支部 */
    private Long parentId;

    /** 支部层级 */
    private Integer branchLevel;

    /** 关联 ERP 组织编码 */
    private String orgCode;

    /** 支部书记姓名 */
    private String secretaryName;

    /** 支部书记用户 ID */
    private Long secretaryUserId;

    /** 成立日期 */
    private LocalDate establishedDate;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
