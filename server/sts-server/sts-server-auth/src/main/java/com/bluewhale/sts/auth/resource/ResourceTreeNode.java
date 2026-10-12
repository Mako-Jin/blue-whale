package com.bluewhale.sts.auth.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源树节点。
 *
 * <p><b>为什么单独一个类而不是给 {@code ResourceVO} 加 {@code children}：</b>
 * 加了之后分页接口返回的每个节点都挂着一个永远为空的 {@code children} 字段，
 * 契约上"平铺列表"与"树"就分不开了 —— 消费方要靠"children 是不是空"猜自己拿到的是哪种。
 * 两个类各自表达一件事，消费方看类型就知道。
 *
 * <p>字段比 {@code ResourceVO} 少：树是**用来选与看层级**的，不需要 {@code requestUrl} /
 * {@code description} 这类明细 —— 需要就走详情接口。少传字段在这里是有意义的，
 * 因为树会一次返回全量节点。
 */
@Getter
@Setter
public class ResourceTreeNode {

    private Long id;

    @Schema(description = "资源标识（即权限码）。也是 parentCode 的引用目标")
    private String resourceCode;

    private String resourceName;

    @Schema(description = "资源类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA")
    private String resourceType;

    @Schema(description = "是否参与后端准入判定（由 resourceType 派生）")
    private Boolean requestScoped;

    private String parentCode;

    private String icon;

    private Integer sortNo;

    @Schema(description = "是否启用：0 否 1 是")
    private Integer isEnabled;

    @Schema(description = "子节点，同级按 sortNo 升序")
    private List<ResourceTreeNode> children = new ArrayList<>();

}
