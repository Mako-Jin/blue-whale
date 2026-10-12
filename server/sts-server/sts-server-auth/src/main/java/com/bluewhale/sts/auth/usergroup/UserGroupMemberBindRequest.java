package com.bluewhale.sts.auth.usergroup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 把用户加进用户组的入参。
 *
 * <p><b>{@code memberType} 存在且必填，但当前只有 {@code USER} 被接受</b> ——
 * 传 {@code GROUP} 会得到 40001。这不是"还没做"，而是**故意挡住一条会产生假象的配置**：
 * 组的嵌套在判定链路里不生效（两条判定 SQL 都把 {@code gm.member_type} 钉成了
 * {@code 'USER'}），所以"把 B 组放进 A 组"这类关系会出现在成员列表里、
 * 却不给任何人带来权限。详见 {@code MemberType} 的注释。
 *
 * <p><b>既然只接受一个值，为什么还要这个字段？</b>两个理由：
 * <ul>
 *   <li>删掉它就得把"这个成员是用户"写死在服务层，而表是**多态**的、
 *       历史上可能存在直接写库产生的 {@code GROUP} 行，撤销路径仍需要按类型定位；</li>
 *   <li>将来若补上嵌套（需要 {@code WITH RECURSIVE} 且要在达梦实测），
 *       接口形状不必再改一次 —— 改动会落在服务层的那个判断上，而不是契约上。</li>
 * </ul>
 * 形状上的"多一个必填字段"换来的是一条**显式拒绝**而不是静默接受。
 *
 * <p>{@code effectiveFrom / effectiveTo} 是"临时组员"的表达方式，与角色成员关系完全同义：
 * 传了它，判定侧就只在这段时间内认这段关系（见 {@code AuthorizationMapper}），
 * 到期自动失效，**不需要任何定时任务去删数据**。写反（起始晚于结束）会被服务层拒绝：
 * 那是一段永远不会生效的关系，而它在列表里看起来完全正常。
 *
 * <p>用 {@code memberId}（主键）而不是用户名：关系表里存的就是主键，
 * 少一次转换就少一次出错的机会；用户名可改，主键不可改 ——
 * 把可变的标识写进长期契约是隐患。与 {@code RoleMemberBindRequest} 同一条。
 */
@Schema(description = "用户组成员入参")
public class UserGroupMemberBindRequest {

    /**
     * 成员类型。用 {@code String} 而不是枚举：枚举反序列化失败时抛的异常
     * 会绕过本域的错误码体系，收成字符串后由服务层归一，非法值能返回 40001
     * 并给出合法取值列表。与 {@code RoleMemberBindRequest.memberType} 一致。
     */
    @NotBlank(message = "成员类型不能为空（当前只支持 USER）")
    @Schema(description = "成员类型。**当前只接受 USER**：GROUP（组嵌套）在判定链路里不生效，"
            + "传它会返回 40001 而不是被静默接受", example = "USER")
    private String memberType;

    @NotNull(message = "成员主键不能为空")
    @Schema(description = "成员主键（用户主键）", example = "1")
    private Long memberId;

    @Schema(description = "生效起始时间（毫秒时间戳），不传表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），不传表示长期有效")
    private Long effectiveTo;

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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
