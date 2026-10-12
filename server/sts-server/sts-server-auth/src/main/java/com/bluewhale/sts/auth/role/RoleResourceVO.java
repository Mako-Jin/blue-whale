package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色已授予资源的一条记录 —— 授权管理界面的行。
 *
 * <p>它是 {@code bw_auth_rel_role_resource} 与 {@code bw_auth_resource} 的合成视图：
 * 关系表提供"授了、什么效果、什么时间段"，资源表提供"授的是哪个权限码、叫什么、
 * 是菜单还是按钮"。两边都必要 —— 只给 {@code resource_id} 的界面没法用，
 * 只给权限码的界面没法回传主键去撤销。
 *
 * <p>带 {@code resourceType} 是因为它决定这条授权的**性质**：
 * {@code API} / {@code BUTTON} 参与后端判定，{@code MENU} / {@code PAGE} 只影响前端可见性
 * （见 {@code ResourceType.isRequestScoped()}）。授权界面把两者混在一列里，
 * 会让"授了菜单怎么还是没权限"变成一个反复出现的问题。
 */
@Getter
@Setter
@Schema(description = "角色已授予的资源")
public class RoleResourceVO {

    @Schema(description = "授权关系主键，撤销时用它（不是资源主键）")
    private Long grantId;

    @Schema(description = "资源主键")
    private Long resourceId;

    @Schema(description = "资源标识（即权限码），如 auth:user:create")
    private String resourceCode;

    private String resourceName;

    @Schema(description = "资源类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA")
    private String resourceType;

    @Schema(description = "该资源是否参与后端准入。由资源类型推导 —— "
            + "API / BUTTON 为 true，MENU / PAGE / DATA 等为 false（可见性不是安全边界）")
    private Boolean requestScoped;

    @Schema(description = "资源是否启用。停用的资源不再产生权限")
    private Integer isEnabled;

    @Schema(description = "效果：ALLOW 允许 / DENY 显式拒绝（优先）")
    private String effect;

    private Integer priority;

    @Schema(description = "生效起始时间（毫秒时间戳），null 表示立即生效")
    private Long effectiveFrom;

    @Schema(description = "生效结束时间（毫秒时间戳），null 表示长期有效")
    private Long effectiveTo;

    /**
     * 这条授权此刻是否生效。
     *
     * <p><b>它是派生字段，由服务层按当前时间算出，不接受入参。</b>
     * 有它的理由：时态授权的"当前时间"是判定的一部分，界面若不显示这个结论，
     * 使用者就得自己在脑子里比对两个时间戳 —— 而"授权列表里有这一行"
     * 与"这条授权真的在生效"是两件事，混淆它们会直接导致
     * "明明授了权为什么还是 403"这类排查。
     */
    @Schema(description = "此刻是否落在生效区间内。false 表示尚未生效或已过期")
    private Boolean effectiveNow;

    @Schema(description = "授权建立时间（毫秒时间戳）")
    private Long createTime;

}
