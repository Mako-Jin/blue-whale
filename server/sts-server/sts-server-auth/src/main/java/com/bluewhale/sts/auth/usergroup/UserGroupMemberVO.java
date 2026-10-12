package com.bluewhale.sts.auth.usergroup;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户组成员关系的一条记录。
 *
 * <p>结构与 {@code RoleMemberVO} **逐字近似**（成员类型 / 成员主键 / 成员名 / 时态 /
 * {@code effectiveNow}），却刻意各留一份而不是共用一个：
 * <ul>
 *   <li>两者的**取值域不同** —— 角色成员允许 {@code USER} 与 {@code GROUP}，
 *       组成员当前只允许 {@code USER}。共用会让"这个字段在这边有哪些合法值"
 *       变成一个需要看两个上下文才能回答的问题，而它恰好是这两处最容易搞错的地方；</li>
 *   <li>两者的**名字解析来源不同** —— 组成员只为 {@code USER} 查用户表，
 *       角色成员还要为 {@code GROUP} 查用户组表。共用意味着这个类要永远带着
 *       "用户组那一半可能用不上"的分支。</li>
 * </ul>
 * 下沉的触发条件是**第三个**成员关系出现（组织-用户是下一个候选）——
 * 到那时三份的公共部分才由三个真实用例定形，与"模块按需创建，不预置空壳"同一条原则。
 *
 * <p>{@code memberName} 允许为 null：多态表没有外键（见 {@code MemberType} 的注释），
 * "加过、但那个人已经被删了"这种关系行是可能存在的，而它恰恰是
 * 排查"为什么权限对不上"时要看的东西 —— 过滤掉它等于把这段历史抹了。
 *
 * <p><b>只表达时态，不表达"这个组是否启用"。</b>那由 {@code GET /api/auth/user-groups/{id}}
 * 回答（界面本来就会加载它）。把多个开关压成一个布尔，会让
 * "到底哪个开关关着"不可见 —— 而那正是排查权限问题的第一个问题。
 */
@Getter
@Setter
@Schema(description = "用户组成员关系")
public class UserGroupMemberVO {

    @Schema(description = "成员关系主键，移除时用它")
    private Long grantId;

    @Schema(description = "成员类型。当前恒为 USER")
    private String memberType;

    @Schema(description = "成员类型的中文名，供界面直接展示")
    private String memberTypeLabel;

    @Schema(description = "成员主键（用户主键）")
    private Long memberId;

    @Schema(description = "成员名称：取用户 displayName（为空则回退 username）。"
            + "用户已不存在时为 null —— 多态表没有外键，这种情况是可能的，且应当被看见")
    private String memberName;

    @Schema(description = "生效起始时间（毫秒时间戳），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），null 表示长期有效")
    private Long effectiveTo;

    @Schema(description = "此刻是否落在生效区间内。false 表示尚未生效或已过期。"
            + "它只回答时态问题：用户组被停用时即使这里为 true，权限依然不会产生")
    private Boolean effectiveNow;

    @Schema(description = "成员关系建立时间（毫秒时间戳）")
    private Long createTime;

}
