package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 反查结果的一行 —— **这个用户的某个角色是从哪来的**。
 *
 * <p>{@code GET /api/auth/users/{id}/roles} 的返回元素。它回答的是排查权限问题时
 * 的第一个问题，因此与 {@code RoleMemberVO}（从角色看成员）方向相反、取舍也不同：
 *
 * <p><b>"经用户组"的一行实际是两条边串起来的</b>：
 * <pre>
 *   直接授予  用户 ──(rel_role_member, USER)──→ 角色
 *   经用户组  用户 ──(rel_user_group_member, USER)──→ 用户组 ──(rel_role_member, GROUP)──→ 角色
 * </pre>
 * 两条边各自有自己的生效区间，所以本类带**两组**时态字段（{@code effectiveFrom/To}
 * 是"角色成员"那条边，{@code groupMemberEffectiveFrom/To} 是"组成员"那条边）。
 * 只留一组会让"用户已经不在这个组里了，为什么还显示这条角色"无从解释。
 * {@code source = DIRECT} 时第二组全为 {@code null} —— 那条边不存在，而不是"长期有效"。
 *
 * <p><b>不把多个开关压成一个布尔</b>，与 {@code RoleMemberVO} 同一条原则：
 * 一行能不能真的产生权限，由"两条边各自的时态 + 角色是否启用 +（组场景）组是否启用"
 * 共同决定。压成一个 {@code active} 会让"到底哪个开关关着"不可见，
 * 而那正是这张表要回答的问题。因此这里给的是**四个独立的可判读字段**：
 * {@code effectiveNow} / {@code groupMemberEffectiveNow} / {@code roleStatus} /
 * {@code groupEnabled}，由界面组合表达。
 *
 * <p><b>刻意不过滤。</b>列表里出现一行"角色已停用"或"组已停用"是**有价值的** ——
 * 它正好解释了"为什么配了权限却没有"。若在 SQL 里按判定条件过滤，
 * 反查接口就会退化成 {@code /api/auth/me} 的一个副本，
 * 而"我明明授了权，怎么没有"这个问题依旧没人能回答。
 */
@Getter
@Setter
@Schema(description = "用户角色的来源（反查）")
public class UserRoleVO {

    @Schema(description = "角色成员关系主键（rel_role_member 那一行的 id）")
    private Long grantId;

    @Schema(description = "来源：DIRECT 直接授予 / GROUP 经用户组授予")
    private String source;

    @Schema(description = "来源的中文名，供界面直接展示（直接授予 / 经用户组授予）")
    private String sourceLabel;

    @Schema(description = "角色主键")
    private Long roleId;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色状态：ENABLED / DISABLED。**停用的角色不产生权限**，"
            + "但这里照原样返回 —— 它正是「为什么没有权限」的答案之一")
    private String roleStatus;

    @Schema(description = "来源用户组主键。仅 source = GROUP 时有值")
    private Long groupId;

    @Schema(description = "来源用户组编码。仅 source = GROUP 时有值。**用户组名不要求唯一**"
            + "（与角色、组织同构，见 decisions/0061），所以这一列是界面上区分同名组的唯一手段 ——"
            + "角色的那三个标识（id / code / name）在这里必须对应齐")
    private String groupCode;

    @Schema(description = "来源用户组名称。仅 source = GROUP 时有值")
    private String groupName;

    @Schema(description = "来源用户组是否启用（0 否 1 是）。仅 source = GROUP 时有值。"
            + "**停用的组不产生权限**，同样照原样返回")
    private Integer groupEnabled;

    @Schema(description = "角色成员关系的生效起始时间（毫秒），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "角色成员关系的生效结束时间（毫秒），null 表示长期有效")
    private Long effectiveTo;

    @Schema(description = "角色成员关系此刻是否落在生效区间内")
    private Boolean effectiveNow;

    @Schema(description = "来源组成员关系的生效起始时间（毫秒）。仅 source = GROUP 时有值")
    private Long groupMemberEffectiveFrom;

    @Schema(description = "来源组成员关系的生效结束时间（毫秒）。仅 source = GROUP 时有值")
    private Long groupMemberEffectiveTo;

    @Schema(description = "来源组成员关系此刻是否落在生效区间内。仅 source = GROUP 时有值")
    private Boolean groupMemberEffectiveNow;

    @Schema(description = "角色成员关系建立时间（毫秒时间戳）")
    private Long createTime;

}
