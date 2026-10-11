package com.bluewhale.sts.auth.user;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/** 用户分页查询条件 */
@Schema(description = "用户查询条件")
public class UserQuery extends PageQuery {

    @Schema(description = "租户主键，不传则限定为当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "关键字，匹配登录名、姓名、邮箱")
    private String keyword;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

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

}
