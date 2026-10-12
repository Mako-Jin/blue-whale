package com.bluewhale.sts.auth.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 资源新增 / 修改入参。
 *
 * <p><b>{@code resourceCode} 的形状校验刻意写成注解</b>（而不是服务层的 if）：
 * 注解会进 OpenAPI 契约，于是"资源标识长什么样"这件事在**契约里可见**，
 * 前端能在提交前拦下一半错误；服务层即便不查也不会漏 —— 两端的校验密度不同，
 * 但依据是同一个正则。
 *
 * <p><b>它与 {@code AuthTaxonomyTest} 里的正则必须一致。</b>那边管住"代码里声明的常量"，
 * 这边管住"从接口登记进来的值"；两处若不形成同一形状，分类学保证就会被绕过 ——
 * 一个从接口写进去的畸形标识（比如两段的 {@code auth:user}）会让
 * "每个权限码都能归到某个资源下"这条断言在**存量数据**上失效，
 * 而测试只看得见常量，看不见库。
 *
 * <p><b>没有 tenantId 字段</b>：资源目录当前是平台级的（{@code tenant_id} 留空），
 * 与种子数据一致。理由见 {@code ResourceService} 的类注释。
 */
@Schema(description = "资源保存入参")
public class ResourceSaveRequest {

    @NotBlank(message = "资源标识不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$",
            message = "资源标识须为「域:资源:动作」三段小写，冒号分隔，例如 auth:user:create")
    @Size(max = 128, message = "资源标识不能超过 128 字")
    @Schema(description = "资源标识（即权限码）。创建后不可修改",
            example = "auth:user:create")
    private String resourceCode;

    @NotBlank(message = "资源名称不能为空")
    @Size(max = 128, message = "资源名称不能超过 128 字")
    private String resourceName;

    @NotBlank(message = "资源类型不能为空")
    @Schema(description = "资源类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA",
            example = "MENU")
    private String resourceType;

    @Size(max = 64, message = "业务分类不能超过 64 字")
    @Schema(description = "业务分类，用于分组展示")
    private String resourceCategory;

    @Size(max = 128, message = "父资源标识不能超过 128 字")
    @Schema(description = "父资源的资源标识（注意是标识不是主键），顶级不传。构成树")
    private String parentCode;

    @Size(max = 512, message = "请求地址不能超过 512 字")
    @Schema(description = "请求地址。仅供参考与排查，**不参与运行时判定** —— 判定走 @RequiresPermission")
    private String requestUrl;

    @Size(max = 16, message = "请求方法不能超过 16 字")
    @Schema(description = "请求方法，如 GET / POST")
    private String requestMethod;

    @Size(max = 128, message = "图标不能超过 128 字")
    @Schema(description = "图标标识。MENU / PAGE 类型用它渲染导航")
    private String icon;

    @Schema(description = "排序号，同级内升序；缺省 0")
    private Integer sortNo;

    @Schema(description = "是否启用：0 否 1 是，缺省 1。停用的资源不再产生权限")
    private Integer isEnabled;

    @Schema(description = "是否免鉴权白名单：0 否 1 是，缺省 0。**这是安全评审要看的一份清单**")
    private Integer isWhitelist;

    @Schema(description = "是否已废弃：0 否 1 是，缺省 0")
    private Integer isDeprecated;

    @Size(max = 32, message = "资源版本号不能超过 32 字")
    @Schema(description = "资源版本号。**当前只存不用**，版本治理尚未实现")
    private String resourceVersion;

    @Size(max = 512, message = "描述不能超过 512 字")
    private String description;

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceCategory() {
        return resourceCategory;
    }

    public void setResourceCategory(String resourceCategory) {
        this.resourceCategory = resourceCategory;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

    public Integer getIsWhitelist() {
        return isWhitelist;
    }

    public void setIsWhitelist(Integer isWhitelist) {
        this.isWhitelist = isWhitelist;
    }

    public Integer getIsDeprecated() {
        return isDeprecated;
    }

    public void setIsDeprecated(Integer isDeprecated) {
        this.isDeprecated = isDeprecated;
    }

    public String getResourceVersion() {
        return resourceVersion;
    }

    public void setResourceVersion(String resourceVersion) {
        this.resourceVersion = resourceVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
