package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织成员关系的一条记录（一个用户在某个组织下的归属）。
 *
 * <p>结构与 {@code UserGroupMemberVO} 逐字近似（成员主键 / 成员名 / 时态 /
 * {@code effectiveNow}），仍各留一份 —— 这是**第三份**同形的成员视图。
 * {@code UserGroupMemberVO} 的注释里写着"下沉的触发条件是第三个成员关系出现
 * （组织-用户是下一个候选）"。**这一轮就是那个时刻**，但结论仍然是暂不下沉，理由与当时不同：
 * <ul>
 *   <li>前两份的差别是**取值域**（角色成员允许用户与组、组成员只允许用户），
 *       这一份的差别是**多了一个决定权限的字段** —— {@code isPrimaryPosition}。
 *       把它塞进一个共用类，会让角色成员关系也带着一个永远为 null 的"是否主岗"；</li>
 *   <li>真要下沉，公共部分应当由三个候选的**交集**定形，而本轮的第三份恰好带来了
 *       一个不共有的字段 —— 说明"公共部分"此刻并不存在。</li>
 * </ul>
 * 触发条件下调为"第四个成员关系，或三者的字段集第一次出现真包含关系"。
 *
 * <p><b>{@code isPrimaryPosition} 是本类唯一影响权限的字段</b>，
 * 因此它被显式地翻译成一个独立布尔下发，而不只是一个 0/1：
 * 前端读 {@code primaryPosition} 就能直接决定要不要把它标成"主岗"，
 * 不必记住"1 是是、0 是否"。
 *
 * <p>{@code memberName} 允许为 null：关系表没有外键（用户可被逻辑删除），
 * "挂过、但那个人已经没了"这种行是可能存在的，而它恰恰是排查
 * "为什么这个部门的人数是 3 但只看到 2 个人"时要看的东西 —— 过滤掉它等于把这段历史抹了。
 * 与 {@code UserGroupMemberVO} 同一条。
 */
@Getter
@Setter
@Schema(description = "组织成员关系（用户在组织下的归属）")
public class OrgMemberVO {

    @Schema(description = "成员关系主键，撤销时用它")
    private Long grantId;

    @Schema(description = "成员主键（用户主键）")
    private Long memberId;

    @Schema(description = "成员名称：取用户 displayName（为空则回退 username）。"
            + "用户已不存在时为 null —— 关系表没有外键，这种情况是可能的，且应当被看见")
    private String memberName;

    @Schema(description = "是否主岗：0 否 1 是。**主岗是 DEPT / DEPT_AND_CHILD 档位唯一的取值来源**")
    private Integer isPrimaryPosition;

    @Schema(description = "是否主岗的布尔形态，供界面直接绑定（等于 isPrimaryPosition == 1）")
    private Boolean primaryPosition;

    @Schema(description = "岗位名")
    private String positionName;

    @Schema(description = "关系类型。当前没有任何判定读它")
    private String relationType;

    @Schema(description = "生效起始时间（毫秒时间戳），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），null 表示长期有效")
    private Long effectiveTo;

    @Schema(description = "此刻是否落在生效区间内。false 表示尚未生效或已过期 —— "
            + "过期的归属不参与数据范围求值，见 DataScopeService")
    private Boolean effectiveNow;

    @Schema(description = "归属关系建立时间（毫秒时间戳）")
    private Long createTime;

}
