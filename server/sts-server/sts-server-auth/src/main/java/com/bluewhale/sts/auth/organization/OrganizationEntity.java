package com.bluewhale.sts.auth.organization;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织 / 部门实体，对应表 {@code bw_auth_organization}。
 *
 * <p>与租户一样用 {@code parentId / orgLevel / codePath} 三列表达层级。
 * 数据权限里的"本部门""本部门及下级"两档就建立在这个结构上。
 *
 * <p>{@code managerName} 是冗余列：组织列表要展示负责人姓名，
 * 若不冗余就得在列表接口里对每个组织再查一次用户表（N+1）。
 * 冗余的代价是改名时要同步，这个代价换来的是一次查询 —— 值得。
 */
@Getter
@Setter
@TableName("bw_auth_organization")
public class OrganizationEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 组织编码，租户内唯一 */
    private String orgCode;

    private String orgName;

    private String fullName;

    private Long parentId;

    private Integer orgLevel;

    private String codePath;

    private Integer hasChild;

    /** 组织类型：COMPANY / DEPT / TEAM ... */
    private String orgType;

    private Long managerId;

    /** 负责人姓名（冗余，便于列表展示） */
    private String managerName;

    private Integer sortNo;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private String description;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

}
