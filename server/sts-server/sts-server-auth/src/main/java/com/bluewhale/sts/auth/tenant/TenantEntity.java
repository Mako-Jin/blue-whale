package com.bluewhale.sts.auth.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户实体，对应表 {@code bw_auth_tenant}。
 *
 * <p><b>表名与参照实现的差异（必须修掉的缺陷）：</b>
 * {@code SystemTerminator} 的 {@code TenantInfoPo} 标的是 {@code auth_tbl_tenant_info}，
 * 而建表脚本建的是 {@code auth_tbl_tenant} —— 两处对不上，所有租户查询直接 SQL 报错。
 * 这里从源头统一：**类名、@TableName、建表脚本三处同名**，并让实体映射测试钉住。
 *
 * <p>层级关系用三列表达：{@code parentId}（父节点）、{@code tenantLevel}（深度）、
 * {@code codePath}（物化路径，形如 {@code /1/7/23}）。
 * 物化路径是为了"取某租户的全部下级"这类查询能一次命中，而不必递归；
 * 代价是移动节点时要重写子树路径，这个代价在租户这种低频变更的场景可以接受。
 */
@Getter
@Setter
@TableName("bw_auth_tenant")
public class TenantEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码，对外稳定标识，全局唯一 */
    private String tenantCode;

    private String tenantName;

    /** 父租户主键，顶级为 null */
    private Long parentId;

    /** 层级，顶级为 1 */
    private Integer tenantLevel;

    /** 物化路径 */
    private String codePath;

    /** 是否有子节点：0 否 1 是。用冗余列换"展开树时不必逐节点 count" */
    private Integer hasChild;

    /** 状态：ENABLED / DISABLED，取值见 {@code Status} */
    private String status;

    private String description;

    /** 是否允许自助注册：0 否 1 是 */
    private Integer allowRegister;

    /** 是否允许下级创建租户：0 否 1 是 */
    private Integer allowAdd;

}
