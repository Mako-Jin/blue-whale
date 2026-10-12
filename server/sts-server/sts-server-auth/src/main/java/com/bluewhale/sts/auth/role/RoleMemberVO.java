package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色成员关系的一条记录 —— 成员管理界面的行。
 *
 * <p>它是 {@code bw_auth_rel_role_member} 与**成员自身那张表**的合成视图：
 * 关系表提供"什么时候授的、有没有时间段"，成员表提供"授的是谁、叫什么"。
 * 与 {@code RoleResourceVO} 同构，只有一处不同值得说明。
 *
 * <p><b>成员名是跨两张表解析出来的，所以没有用 JOIN 取。</b>
 * 资源授权那边一条 JOIN 就够，因为被授对象只有 {@code bw_auth_resource} 一张表；
 * 这里的被授对象是**多态**的（{@code bw_auth_user} 或 {@code bw_auth_user_group}），
 * 用 JOIN 就得写成 {@code CASE WHEN member_type = 'USER' THEN u.display_name
 * ELSE g.group_name END} 再挂两个 {@code LEFT JOIN} —— 一条 SQL 同时被两张表的
 * 方言细节影响，而且 {@code bw_auth_user_group} 目前还没有任何接口，
 * 它的存在对该查询是个不能失败的依赖。改为"先取关系行，再按类型分两次批量查名字"：
 * 总共 3 条查询、与 {@code IN} 的规模无关，且每一条都能单独看懂。
 *
 * <p><b>为什么带 {@code memberName} 的 null 是允许的：</b>多态表上没有外键
 * （见 {@code MemberType} 的注释：放弃外键换来"追溯只有一条路径"），
 * 所以理论上可能存在指向已消失成员的关系行。名字缺失时返回 {@code null} 而不是
 * 报错或过滤掉 —— 过滤掉会让"授过、但那个人已经被删了"这一段历史凭空消失，
 * 而它恰恰是排查"为什么权限对不上"时要看的东西。
 *
 * <p><b>刻意不带角色的状态。</b>一段成员关系是否真的在产生权限，由三个独立开关共同决定：
 * 关系自身的时态（本类的 {@code effectiveNow}）、角色的状态、成员自身是否可用。
 * 把它们压成一个布尔，会让"到底哪个开关关着"变成不可见 —— 而这正是排查权限问题的
 * 第一个问题。成员列表负责回答"这个角色给了谁"，"这个角色现在是不是停用"
 * 由 {@code GET /api/auth/roles/{id}} 回答（界面本来就会加载它，两次查询不会打架）。
 * {@code effectiveNow} 因此与 {@code RoleResourceVO} 里那一个**同名同义**：
 * 只表达关系自身的时态，不表达任何别的东西。
 */
@Getter
@Setter
@Schema(description = "角色成员关系")
public class RoleMemberVO {

    @Schema(description = "成员关系主键，撤销时用它")
    private Long grantId;

    @Schema(description = "成员类型：USER / GROUP")
    private String memberType;

    @Schema(description = "成员类型的中文名，供界面直接展示（用户 / 用户组）")
    private String memberTypeLabel;

    @Schema(description = "成员主键（用户主键或用户组主键）")
    private Long memberId;

    @Schema(description = "成员名称：用户取 displayName（为空则回退 username），用户组取 groupName。"
            + "成员已不存在时为 null —— 多态表没有外键，这种情况是可能的，且应当被看见")
    private String memberName;

    @Schema(description = "生效起始时间（毫秒时间戳），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），null 表示长期有效")
    private Long effectiveTo;

    /**
     * 这段成员关系此刻是否落在生效区间内。派生字段，由服务层按当前时间算出，不接受入参。
     *
     * <p>与 {@code RoleResourceVO.effectiveNow} 同名同义，是两个界面之间的对称 ——
     * "列表里有这一行"与"这一段真的在生效"是两件事，混淆它们会直接导致
     * "明明加进角色了为什么还是 403"这类排查。
     *
     * <p>注意它**只**回答时态问题：角色被停用、成员自身被停用时，即使这里为 true，
     * 权限依然不会产生。那两件事各自有明确的归属界面，不在这里重复表达。
     */
    @Schema(description = "此刻是否落在生效区间内。false 表示尚未生效或已过期")
    private Boolean effectiveNow;

    @Schema(description = "成员关系建立时间（毫秒时间戳）")
    private Long createTime;

}
