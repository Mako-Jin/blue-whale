package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 把用户挂到组织下的入参。
 *
 * <p><b>与 {@code UserGroupMemberBindRequest} 有一处关键差别：没有 {@code memberType}。</b>
 * 用户组的成员表是多态的（用户 / 用户组），所以那边的类型是必填；而
 * {@code bw_auth_rel_org_user} **只有用户这一种成员** —— 组织不装组织，
 * 层级由 {@code parent_id} 表达。没有多态列就不要假装有：多一个恒为
 * {@code USER} 的必填字段，只会让人猜它还能是什么。
 *
 * <p><b>{@code isPrimaryPosition} 在这里可以设，但"改主岗"另有一个接口。</b>
 * 区别在语义与留痕：新增归属时**顺带**声明它是主岗，与"把某人现有的主岗从 A 部门
 * 搬到 B 部门"是两件事 —— 后者不改变任何计数（归属条数不变），
 * 却直接改变这个人的 DEPT 数据范围。因此它有自己的审计动作，
 * 见 {@code OrganizationMemberService.setPrimaryPosition}。
 *
 * <p>不传 {@code isPrimaryPosition} 时的行为值得写下来：**若这是该用户的第一个归属，
 * 自动设为主岗；否则不设**。理由是没有主岗的人 DEPT 档位算不出范围，
 * 会被往窄兜底成"仅本人"—— 一个刚建好的账号挂进部门后，
 * 管理员看到的应该是"他在这个部门"，而不是"他挂进去了但什么也看不到"。
 * 反过来，对已经有主岗的人静默改主岗是**不能接受**的：那会悄悄搬动他的数据范围，
 * 而他本人的部门归属看起来毫无变化。
 *
 * <p>{@code positionName}（岗位名）与 {@code relationType}（MEMBER / LEADER …）
 * 都是描述性字段：前者展示，后者当前**没有任何判定读它**。这一点与
 * {@code orgType} 同一条判断 —— 描述存下来，不承诺行为。
 */
@Schema(description = "组织成员（用户归属）入参")
public class OrgMemberBindRequest {

    @NotNull(message = "成员主键不能为空")
    @Schema(description = "成员主键（用户主键）", example = "1")
    private Long memberId;

    @Schema(description = "是否设为主岗：0 否 1 是。**主岗决定这个人的 DEPT 数据范围**。"
            + "不传时：该用户当前没有主岗则自动设为主岗，已有主岗则保持不变（不静默搬动范围）")
    private Integer isPrimaryPosition;

    @Schema(description = "岗位名，只作展示", example = "前端负责人")
    private String positionName;

    @Schema(description = "关系类型（MEMBER / LEADER …），**当前没有任何判定读它**", example = "MEMBER")
    private String relationType;

    @Schema(description = "生效起始时间（毫秒时间戳），不传表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），不传表示长期有效")
    private Long effectiveTo;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Integer getIsPrimaryPosition() {
        return isPrimaryPosition;
    }

    public void setIsPrimaryPosition(Integer isPrimaryPosition) {
        this.isPrimaryPosition = isPrimaryPosition;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Long getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Long effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Long getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Long effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

}
