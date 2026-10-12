package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 给角色授予资源（即授予一个权限码）的入参。
 *
 * <p><b>用 {@code resourceId} 而不是 {@code resourceCode} 引用资源。</b>
 * 两者都能唯一确定一行（资源标识在 ADR-0021 里被钉为不可修改，所以它也稳定），
 * 但请求体里同时支持两种写法会带来"两者不一致时听谁的"这类问题 ——
 * 而这种问题只在真的有人同时传了两个字段时才暴露，排查成本远高于收益。
 * 选主键，是因为授权表里存的就是 {@code resource_id}，少一次转换就少一次出错的机会；
 * 调用方（授权界面）本来也是从资源树上点选，树里就有主键。
 *
 * <p><b>{@code effectiveFrom / effectiveTo} 是"临时代理"的表达方式。</b>
 * 传了它，判定侧就只在这段时间内认这条授权（见 {@code AuthorizationMapper}），
 * 到期自动失效，**不需要任何定时任务去删数据**。两列都可空，都空表示长期有效。
 * 写反（起始晚于结束）会被服务层拒绝：那是一条永远不会生效的授权，
 * 而它看起来完全正常。
 *
 * <p><b>{@code effect} 只允许 ALLOW 与 DENY</b>，缺省 ALLOW。DENY 是"排除"的表达方式：
 * 用户同时持有 A、B 两个角色时，A 允许、B 拒绝，结果是**拒绝** —— 判定按资源聚合，
 * 只要有一条 DENY 命中就不给。这一条是本切片才真正生效的（此前 DENY 只在单角色内成立）。
 */
@Schema(description = "角色授权入参")
public class RoleResourceGrantRequest {

    @NotNull(message = "资源主键不能为空")
    @Schema(description = "资源主键（不是资源标识）", example = "1")
    private Long resourceId;

    @Schema(description = "效果：ALLOW 允许 / DENY 显式拒绝（优先），缺省 ALLOW", example = "ALLOW")
    private String effect;

    @Schema(description = "优先级，用于多条规则冲突时的裁决；缺省 0")
    private Integer priority;

    @Schema(description = "生效起始时间（毫秒时间戳），不传表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），不传表示长期有效")
    private Long effectiveTo;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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
