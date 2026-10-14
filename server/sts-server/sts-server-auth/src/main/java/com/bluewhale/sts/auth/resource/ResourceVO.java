package com.bluewhale.sts.auth.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 资源出参。
 *
 * <p><b>字段与 {@code ResourceEntity} 一一对应，外加一个派生的 {@code requestScoped}。</b>
 * 派生字段的意义是把 {@code ResourceType.isRequestScoped()} 的判断**摆到界面上**：
 * 管理员在登记一条 {@code MENU} 时应当能直接看到"此权限不参与后端拦截"，
 * 而不是自己记住九个类型里哪两个才算。这个信息在代码里是枚举的一个方法，
 * 在界面上一旦缺失，人就会凭感觉假设"登记了权限就拦得住"。
 *
 * <p>它是**只读派生**的，不在保存入参里 —— 从 {@code resourceType} 推得，
 * 让调用方能传一份与之矛盾的 {@code requestScoped} 只会制造"两个真相"。
 *
 * <p><b>没有 {@code hasChild}</b>（租户出参有）：{@code bw_auth_resource} 没有这个列，
 * 逐行算一次"有没有子节点"在分页列表上就是 N+1 次查询。要层级就调 {@code /tree}。
 */
@Getter
@Setter
public class ResourceVO {

    private Long id;

    @Schema(description = "所属租户主键；平台级资源为 null。当前登记进来的都是平台级")
    private Long tenantId;

    @Schema(description = "资源标识（即权限码），创建后不可修改")
    private String resourceCode;

    private String resourceName;

    @Schema(description = "资源类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA")
    private String resourceType;

    @Schema(description = "是否参与后端准入判定（由 resourceType 派生）。MENU / PAGE / DATA 均为 false —— 它们影响前端可见性与查询范围，不拦接口")
    private Boolean requestScoped;

    private String resourceCategory;

    @Schema(description = "父资源的资源标识。构成树")
    private String parentCode;

    @Schema(description = "请求地址，仅供参考，不参与判定")
    private String requestUrl;

    private String requestMethod;

    private String icon;

    private Integer sortNo;

    @Schema(description = "是否启用：0 否 1 是。停用的资源不再产生权限")
    private Integer isEnabled;

    @Schema(description = "人工备注，不参与判定：0 否 1 是。资源是否免鉴权看接口上有没有 @RequiresPermission")
    private Integer isWhitelist;

    @Schema(description = "是否已废弃：0 否 1 是。当前只存不用，没有任何读取方")
    private Integer isDeprecated;

    @Schema(description = "资源版本号，当前只存不用")
    private String resourceVersion;

    private String description;

    private Long createTime;

    private Long updateTime;

}
