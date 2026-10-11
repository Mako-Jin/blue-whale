package com.bluewhale.sts.auth.tenant;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/** 租户分页查询条件 */
@Schema(description = "租户查询条件")
public class TenantQuery extends PageQuery {

    @Schema(description = "关键字，匹配租户编码或名称")
    private String keyword;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

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
