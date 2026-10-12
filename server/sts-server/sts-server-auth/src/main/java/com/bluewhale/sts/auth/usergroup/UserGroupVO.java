package com.bluewhale.sts.auth.usergroup;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户组视图。
 *
 * <p>字段与 {@code UserGroupEntity} 一一对应，除了两列**刻意不返回**的：
 * {@code filterJson} 与 {@code orgScopeJson} —— 它们连入参都不接受
 * （见 {@code UserGroupSaveRequest}），返回一个永远为 null 的字段只会让人以为它有用。
 *
 * <p>{@code isEnabled} 是这里唯一有判定语义的字段：停用的用户组
 * **不再产生权限**（{@code AuthorizationMapper} 会连表检查），
 * 因此界面上的"启用 / 停用"是一个真开关，而不是一个摆设。
 */
@Getter
@Setter
@Schema(description = "用户组")
public class UserGroupVO {

    @Schema(description = "用户组主键")
    private Long id;

    @Schema(description = "所属租户主键")
    private Long tenantId;

    @Schema(description = "用户组编码，租户内唯一")
    private String groupCode;

    @Schema(description = "用户组名称")
    private String groupName;

    @Schema(description = "父用户组主键。只用于列表分组展示，不产生权限")
    private Long parentId;

    @Schema(description = "分组方式。当前恒为 STATIC")
    private String category;

    @Schema(description = "是否默认用户组：0 否 1 是。**当前只存不用**")
    private Integer isDefault;

    @Schema(description = "是否启用：0 否 1 是。**停用的用户组不再产生权限**")
    private Integer isEnabled;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "最后更新时间（毫秒时间戳）")
    private Long updateTime;

}
