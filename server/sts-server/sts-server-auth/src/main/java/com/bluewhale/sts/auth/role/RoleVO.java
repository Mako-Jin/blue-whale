package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色出参。
 *
 * <p>逐字段手写映射（不用 {@code BeanUtils.copyProperties}），与 {@code UserVO} 同一理由：
 * 出参是**对外契约**，多一个字段就是多一次泄露机会，而自动拷贝会让"实体加了字段"
 * 变成"接口悄悄多了一个字段"。代价是新增字段要手动接线 —— 这是刻意的摩擦。
 *
 * <p><b>派生字段 {@code requestScoped} 的教训在这里也适用</b>：{@code dataScope}
 * 是运营最关心的一个字段（"这个角色能看多少数据"），但**它当前不产生任何效果** ——
 * 数据权限的求值层还没做。文档里写清楚，比让使用者以为配了就生效要好。
 */
@Getter
@Setter
@Schema(description = "角色")
public class RoleVO {

    private Long id;

    private Long tenantId;

    @Schema(description = "角色编码，租户内唯一，创建后不可修改")
    private String roleCode;

    private String roleName;

    private Long parentId;

    private Integer roleLevel;

    @Schema(description = "角色分类，仅用于分组展示")
    private String category;

    @Schema(description = "是否默认角色：0 否 1 是。**当前只存不用**")
    private Integer isDefault;

    @Schema(description = "继承策略。**当前只存不用**，角色继承未实现")
    private String inheritStrategy;

    @Schema(description = "数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM。"
            + "**当前只存不用**，数据权限求值未实现 —— 配了不会生效")
    private String dataScope;

    @Schema(description = "自定义数据范围过滤条件。**当前只存不用**")
    private String filterJson;

    private Integer sortNo;

    @Schema(description = "状态：ENABLED / DISABLED。停用的角色不再产生权限")
    private String status;

    private String description;

    private Long createTime;

    private Long updateTime;

}
