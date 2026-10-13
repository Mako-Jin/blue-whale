package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织树节点。
 *
 * <p>与 {@code ResourceTreeNode} 是同一种处理：**单独一个类，而不是给
 * {@code OrganizationVO} 加一个 {@code children}** —— 否则分页接口返回的每一行
 * 都挂着一个永远为空的 {@code children}，契约上"平铺列表"与"树"就分不开了，
 * 消费方要靠"children 是不是空"猜自己拿到的是哪一种。
 *
 * <p>字段比 {@code OrganizationVO} 少一大截，因为两者面对的是不同的场景：
 * 树是**用来选层级、看归属**的，一次返回当前租户的全部节点，
 * 因此只带选择时真正需要的那几列；联系方式、描述这些明细走详情接口。
 *
 * <p>{@code orgLevel} 与 {@code codePath} 留在树里是有意义的 ——
 * 排查数据范围时最常问的两个问题（"这个节点在第几层""它的路径是什么"）在树上就能直接答，
 * 不必点进每个节点。
 */
@Getter
@Setter
public class OrganizationTreeNode {

    private Long id;

    @Schema(description = "组织编码")
    private String orgCode;

    @Schema(description = "组织名称")
    private String orgName;

    @Schema(description = "组织全称，按层级派生")
    private String fullName;

    @Schema(description = "父组织主键，顶级为 null")
    private Long parentId;

    @Schema(description = "层级，根为 1")
    private Integer orgLevel;

    @Schema(description = "物化路径，形如 /1/5/")
    private String codePath;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

    @Schema(description = "负责人姓名（冗余列），便于在树上直接展示")
    private String managerName;

    private Integer sortNo;

    @Schema(description = "子节点，同级按 sortNo 升序、同序按主键升序")
    private List<OrganizationTreeNode> children = new ArrayList<>();

}
