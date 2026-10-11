package com.bluewhale.sts.auth.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 租户新增 / 修改入参 */
@Schema(description = "租户保存入参")
public class TenantSaveRequest {

    @NotBlank(message = "租户编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{1,63}$",
            message = "租户编码须以字母开头，仅含字母、数字、下划线与连字符，长度 2~64")
    @Schema(description = "租户编码", example = "acme")
    private String tenantCode;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不能超过 128 字")
    private String tenantName;

    @Schema(description = "父租户主键，顶级不传")
    private Long parentId;

    @Size(max = 512, message = "描述不能超过 512 字")
    private String description;

    @Schema(description = "状态：ENABLED / DISABLED，缺省 ENABLED")
    private String status;

    @Schema(description = "是否允许自助注册：0 否 1 是")
    private Integer allowRegister;

    @Schema(description = "是否允许下级创建租户：0 否 1 是")
    private Integer allowAdd;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAllowRegister() {
        return allowRegister;
    }

    public void setAllowRegister(Integer allowRegister) {
        this.allowRegister = allowRegister;
    }

    public Integer getAllowAdd() {
        return allowAdd;
    }

    public void setAllowAdd(Integer allowAdd) {
        this.allowAdd = allowAdd;
    }

}
