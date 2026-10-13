package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 移动组织节点的入参。
 *
 * <p><b>为什么移动是一个独立接口，而不是 {@code OrganizationSaveRequest} 里的一个字段：</b>
 * 因为它的**代价与别的字段完全不同**。改个名字只影响一行；移动一棵子树会重写
 * 整棵子树的 {@code code_path} 与 {@code level}，而 {@code DEPT_AND_CHILD} 档位
 * 按路径前缀求值 —— 于是这一次调用的真实效果是"**子树上每个人的可见范围同时改变**"。
 * 这样一次操作必须有自己的审计动作，否则它会被淹没在"某某更新了组织"里。
 *
 * <p>{@code parentId} **允许为 null**，表示把节点提升为顶级。这与"不传"无法区分，
 * 因此请求体是必填的：传 {@code {}} 就是提升为顶级，传 {@code {"parentId": null}}
 * 也是 —— 两者语义相同，不需要第三种表达。若想"不改父级、只改别的字段"，
 * 走 {@code PUT /api/auth/orgs/{id}}，那条路径不碰层级。
 */
@Schema(description = "组织移动入参")
public class OrganizationMoveRequest {

    @Schema(description = "新的父组织主键。**不传或传 null 都表示提升为顶级**",
            example = "3")
    private Long parentId;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

}
