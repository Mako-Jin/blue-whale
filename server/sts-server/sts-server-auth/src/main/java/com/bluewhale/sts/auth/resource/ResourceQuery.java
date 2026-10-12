package com.bluewhale.sts.auth.resource;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 资源分页查询条件。
 *
 * <p>四个条件都是**可选**的，与 {@code TenantQuery} / {@code UserQuery} 一致：
 * 无条件即全量分页。**刻意不做"按 tree 递归取子树"** —— 那是 {@code /tree} 的职责，
 * 分页接口的语义是"平的列表"，混进层级语义会让 {@code total} 的含义变得含糊
 * （是节点数还是含子孙的总数？）。
 */
@Schema(description = "资源查询条件")
public class ResourceQuery extends PageQuery {

    @Schema(description = "关键字，匹配资源标识或名称")
    private String keyword;

    @Schema(description = "资源类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA")
    private String resourceType;

    @Schema(description = "父资源标识，用于只取某一层")
    private String parentCode;

    @Schema(description = "是否启用：0 否 1 是")
    private Integer isEnabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public Integer getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

}
