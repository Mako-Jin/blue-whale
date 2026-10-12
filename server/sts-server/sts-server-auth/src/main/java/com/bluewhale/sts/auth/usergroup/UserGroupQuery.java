package com.bluewhale.sts.auth.usergroup;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户组分页查询条件。
 *
 * <p>四个条件都可选，与 {@code RoleQuery} / {@code UserQuery} 一致。
 *
 * <p><b>租户条件在服务层兜底：</b>不传 {@code tenantId} 时限定为当前登录用户所属租户。
 * 用户组与角色一样**必然**属于某个租户（{@code tenant_id} 是 NOT NULL 且有唯一索引参与），
 * 不能像资源那样留空 —— 留空的组在判定 SQL 里永远匹配不上 {@code gm.tenant_id = ?}，
 * 表现是"建好了、加人进去了、谁都没有权限"。理由与 {@code RoleQuery} 完全相同。
 *
 * <p><b>{@code enabled} 用 {@code Integer} 而不是 {@code Boolean}：</b>
 * 列是 {@code TINYINT}（{@code 0/1}，可为 NULL），用布尔会多一层"null 到底是
 * 不过滤还是查未设置"的歧义，而这个问题在过滤条件里没有意义。
 */
@Schema(description = "用户组查询条件")
public class UserGroupQuery extends PageQuery {

    @Schema(description = "租户主键，不传则限定为当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "关键字，匹配用户组编码或名称")
    private String keyword;

    @Schema(description = "分组方式：STATIC。**过滤条件宽松处理**，认不得的值按原样比")
    private String category;

    @Schema(description = "是否启用：0 / 1，不传表示全部")
    private Integer enabled;

    @Schema(description = "父用户组主键，不传表示全部")
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

}
