package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-资源实例关系，对应表 {@code bw_auth_rel_role_resource_instance}。
 *
 * <p>表达**数据权限**：把授权从"某个资源"细化到"某个资源下的某条数据"。
 * 例如某角色只能审批 {@code instanceId = 1001} 这条流程实例。
 *
 * <p>本切片只落地表与实体，判定逻辑在后续切片补齐 —— 先把存储定下来，
 * 是因为数据权限一旦开始被使用再改表，历史授权数据就要迁移。
 */
@Getter
@Setter
@TableName("bw_auth_rel_role_resource_instance")
public class RelRoleResourceInstanceEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    private Long resourceId;

    private String resourceType;

    /** 数据实例标识 */
    private String instanceId;

    private String instanceName;

    /** 允许的操作集合（JSON 数组） */
    private String actionsJson;

    /** 数据过滤条件（JSON） */
    private String filterJson;

    private Long effectiveFrom;

    private Long effectiveTo;

}
