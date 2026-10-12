package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 把角色授给一个成员（用户或用户组）的入参。
 *
 * <p><b>{@code memberType} 必填，刻意不给默认值。</b>给 {@code USER} 当默认值是最"顺手"
 * 的做法，但它把一次明确的操作变得不明确：调用方想授用户组、却漏了这个字段时，
 * 系统会拿 {@code memberId} 去用户表里找一个**恰好同号的用户**并授给他 ——
 * 一次真实存在的用户被静默提权，而请求里没有任何一处写错过。
 * 两个自增序列撞号是常态，不是边界情况。
 *
 * <p>必填的代价只是调用方多写一个字段，收益是"授错对象"从**可能发生**变成**不可能编译出来**。
 * 与 {@code RoleResourceGrantRequest} 用 {@code resourceId} 而不是同时支持
 * {@code resourceId} / {@code resourceCode} 是同一条思路：形态上让歧义无法表达。
 *
 * <p><b>用 {@code memberId} 而不是用户名 / 组编码：</b>关系表里存的就是主键，
 * 少一次转换就少一次出错的机会；调用方（成员选择界面）本来也是从列表里点选，
 * 列表里就有主键。用户名可改，主键不可改 —— 把可变的标识写进长期契约是隐患。
 *
 * <p><b>{@code effectiveFrom / effectiveTo} 是"临时代理"的表达方式</b>，与角色资源授权
 * 完全同义：传了它，判定侧就只在这段时间内认这段成员关系（见 {@code AuthorizationMapper}），
 * 到期自动失效，**不需要任何定时任务去删数据**。两列都可空，都空表示长期有效。
 * 写反（起始晚于结束）会被服务层拒绝：那是一段永远不会生效的成员关系，
 * 而它在列表里看起来完全正常。
 */
@Schema(description = "角色成员关系入参")
public class RoleMemberBindRequest {

    /**
     * 成员类型。用 {@code String} 而不是枚举：枚举在反序列化失败时抛的是
     * {@code HttpMessageNotReadableException}，落到全局异常处理里是一个与
     * 本域错误码体系无关的响应；收成字符串后由服务层用 {@code MemberType.parse}
     * 归一，非法值能返回 40001 并给出合法取值列表 —— 与 {@code RoleSaveRequest.dataScope}
     * 的处理一致。
     */
    @NotBlank(message = "成员类型不能为空（USER / GROUP）")
    @Schema(description = "成员类型：USER 用户 / GROUP 用户组。**必填，不给默认值** —— "
            + "默认 USER 会让漏填的类型悄悄授给一个同号用户", example = "USER")
    private String memberType;

    @NotNull(message = "成员主键不能为空")
    @Schema(description = "成员主键（用户主键或用户组主键，由 memberType 决定）", example = "1")
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
