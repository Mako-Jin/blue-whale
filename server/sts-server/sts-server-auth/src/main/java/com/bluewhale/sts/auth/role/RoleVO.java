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
 * 是运营最关心的一个字段（"这个角色能看多少数据"），而它的**效果是分层的、必须逐档说清**：
 * ALL 与 SELF 已经影响审计可见性（非 ALL 即「仅本人」）；DEPT 系列能求值出组织集合，
 * 但本仓尚无业务数据表、没有查询消费它；CUSTOM 的 {@code filterJson} 解析未实现，
 * 求值时 {@code precise = false}。写清楚每一档的边界，好过让使用者以为"配了就都生效"——
 * 而**权威口径是求值结果本身**（用户页的「数据范围反查」），不是这里的枚举。
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

    @Schema(description = "是否默认角色（新用户自动授予）：0 否 1 是。"
            + "**已生效** —— 新建用户时按本租户全部为 1、且状态为 ENABLED 的角色自动授予，"
            + "见 ADR-0049")
    private Integer isDefault;

    @Schema(description = "继承策略。**当前只存不用**，角色继承未实现")
    private String inheritStrategy;

    @Schema(description = "数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM。"
            + "ALL 与 SELF 已影响审计可见性（非 ALL 即「仅本人」）；"
            + "DEPT 系列可求值出组织集合，但当前没有任何查询消费它（本仓尚无业务数据表）；"
            + "CUSTOM 的 filterJson 解析未实现，求值时 precise = false")
    private String dataScope;

    @Schema(description = "自定义数据范围过滤条件（JSON），仅 dataScope = CUSTOM 时使用。"
            + "**本仓未实现其解析** —— CUSTOM 档求值结果为 precise = false，"
            + "已接入的查询保守地按「仅本人」处理；管理界面也不提供这一档")
    private String filterJson;

    private Integer sortNo;

    @Schema(description = "状态：ENABLED / DISABLED。停用的角色不再产生权限")
    private String status;

    private String description;

    private Long createTime;

    private Long updateTime;

}
