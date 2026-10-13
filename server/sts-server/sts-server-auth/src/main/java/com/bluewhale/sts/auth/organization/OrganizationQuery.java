package com.bluewhale.sts.auth.organization;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织分页查询条件。
 *
 * <p>与 {@code UserGroupQuery} 同构，取舍也相同：**租户条件在服务层兜底** ——
 * 不传 {@code tenantId} 时限定为当前登录用户所属租户。组织与角色、用户组一样
 * **必然**属于某个租户（{@code tenant_id} 是 NOT NULL 且参与唯一索引），
 * 不能像资源那样留空：留空的组织在数据范围求值里永远匹配不上
 * {@code tenant_id = ?}，表现是"树建好了、人挂进去了，DEPT 档位算出来是空的"。
 *
 * <p><b>为什么不提供 {@code descendantOf} 这类"取某节点的子树"条件：</b>
 * 那属于树接口（{@code GET /api/auth/orgs/tree} + {@code rootId}）的职责。
 * 分页接口按 {@code parentId} 只取**直接下级**，两者回答的是不同的问题 ——
 * 混在一起会让 {@code total} 的含义随参数变化。
 */
@Schema(description = "组织查询条件")
public class OrganizationQuery extends PageQuery {

    @Schema(description = "租户主键，不传则限定为当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "关键字，匹配组织编码、名称或全称")
    private String keyword;

    @Schema(description = "状态：{@code ENABLED} / {@code DISABLED}，**过滤条件宽松处理**，"
            + "认不得的值按原样比（查不到比报错更接近预期）")
    private String status;

    @Schema(description = "组织类型，**只作展示、不参与任何判定**，因此按原样比")
    private String orgType;

    @Schema(description = "父组织主键，不传表示全部（含各级）。取整棵子树请用树接口")
    private Long parentId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

}
