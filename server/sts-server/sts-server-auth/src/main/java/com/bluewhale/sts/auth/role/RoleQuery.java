package com.bluewhale.sts.auth.role;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色分页查询条件。
 *
 * <p>四个条件都是可选的，与 {@code TenantQuery} / {@code UserQuery} / {@code ResourceQuery} 一致。
 *
 * <p><b>租户条件在服务层兜底：</b>不传 {@code tenantId} 时限定为当前登录用户所属租户
 * （与 {@code UserService} 同一条规则）。角色与用户不同，它**必然**属于某个租户
 * （{@code tenant_id} 是 NOT NULL 且有唯一索引参与），所以不能像资源那样留空 ——
 * 留空的角色在判定 SQL 里永远匹配不上 {@code ro.tenant_id = ?}，
 * 表现为"建好了、授权了、谁都没有权限"。
 */
@Schema(description = "角色查询条件")
public class RoleQuery extends PageQuery {

    @Schema(description = "租户主键，不传则限定为当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "关键字，匹配角色编码或角色名称")
    private String keyword;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

    @Schema(description = "角色分类，如 SYSTEM / BUSINESS / CUSTOM。**仅用于分组展示**，不参与判定")
    private String category;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

}
