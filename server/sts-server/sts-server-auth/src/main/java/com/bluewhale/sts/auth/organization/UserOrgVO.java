package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 某个用户的组织归属，**从用户这一侧看**（反查）。
 *
 * <p>为什么不能复用 {@link OrgMemberVO}：那个类回答的是"这个组织里有哪些人"
 * （成员主键 + 成员名 + 主岗），而这里要回答的是"这个人在哪些组织里"——
 * 需要的是**组织**的编码、名称与全称，那个类一个都没有。
 * 把两边合成一个类，会让每个字段都带上"在另一侧为 null"的注释，
 * 而调用方要靠场景猜哪一半有用。
 *
 * <p>{@code orgStatus} 也一并下发，原因与 {@link OrgMemberVO#getEffectiveNow()}
 * 的注释同一支：{@code DEPT} 档位要求主岗所在组织**未停用**才能求值，
 * 所以"我明明是主岗、为什么数据范围还是仅本人"这个问题的答案，
 * 往往就在这一列上。只回一个布尔（能否求值）会让这个原因不可见。
 */
@Getter
@Setter
@Schema(description = "用户在某个组织下的归属（反查：这个人属于哪些组织）")
public class UserOrgVO {

    @Schema(description = "归属关系主键")
    private Long grantId;

    @Schema(description = "组织主键")
    private Long orgId;

    @Schema(description = "组织编码")
    private String orgCode;

    @Schema(description = "组织名称")
    private String orgName;

    @Schema(description = "组织全称，按层级派生")
    private String fullName;

    @Schema(description = "组织状态：ENABLED / DISABLED。**停用的组织不能作为 DEPT 档位的范围目标**")
    private String orgStatus;

    @Schema(description = "是否主岗：0 否 1 是。**主岗是 DEPT / DEPT_AND_CHILD 档位唯一的取值来源**")
    private Integer isPrimaryPosition;

    @Schema(description = "是否主岗的布尔形态，供界面直接绑定")
    private Boolean primaryPosition;

    @Schema(description = "岗位名")
    private String positionName;

    @Schema(description = "关系类型。当前没有任何判定读它")
    private String relationType;

    @Schema(description = "生效起始时间（毫秒时间戳），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），null 表示长期有效")
    private Long effectiveTo;

    @Schema(description = "此刻是否落在生效区间内。**过期的归属不参与数据范围求值**")
    private Boolean effectiveNow;

    @Schema(description = "归属关系建立时间（毫秒时间戳）")
    private Long createTime;

}
