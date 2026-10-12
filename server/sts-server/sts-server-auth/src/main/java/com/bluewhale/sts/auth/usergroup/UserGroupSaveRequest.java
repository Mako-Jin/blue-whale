package com.bluewhale.sts.auth.usergroup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户组新增 / 修改入参。
 *
 * <p><b>字段比表少，这是刻意的。</b>{@code bw_auth_user_group} 上有
 * {@code filterJson} 与 {@code orgScopeJson} 两列，本类**两列都不接受**：
 * 它们的消费方（动态成员求值、组织范围过滤）都还不存在。
 * 接受一个没人读的字段，最坏的后果不是"白存了"，而是调用方**以为它生效了** ——
 * 界面上填了过滤条件、保存成功、成员一个没变，而日志里没有任何异常。
 * 这与"DYNAMIC 分类被显式拒绝"是同一条判断，只是位置不同：
 * 一个是不给写，一个是不给选。
 *
 * <p><b>校验密度分层</b>，与 {@code RoleSaveRequest} 同一条依据（写错了会不会有人受伤）：
 * <ul>
 *   <li><b>有判定语义的严格</b> —— {@code isEnabled} 只接受 0/1。
 *       它在判定链路里是**真开关**（停用的组不再产生权限，见 {@code AuthorizationMapper}），
 *       放行别的值不会报错，只会静默改变语义；</li>
 *   <li><b>只存不用的宽松</b> —— {@code category} 由服务层收窄成 {@code STATIC}
 *       （见 {@code UserGroupService.resolveCategory}）、{@code isDefault} 只校验 0/1
 *       但**当前没有任何消费方**（"新用户自动入组"未实现），
 *       与 {@code RoleSaveRequest.isDefault} 是同一种处理；</li>
 *   <li><b>纯粹组织信息的不校验</b> —— {@code parentId} / {@code sortNo} / {@code description}。
 *       {@code parentId} 只用于列表分组展示，**不产生任何权限**（见 {@code UserGroupEntity}）。</li>
 * </ul>
 *
 * <p>{@code groupCode} 的形状用注解而不是服务层 if，理由同 {@code ResourceSaveRequest}：
 * 注解会进 OpenAPI 契约，前端能在提交前拦下一半错误。
 * <b>不强制大小写转换</b> —— 那会让 {@code fin_group} 与 {@code FIN_GROUP}
 * 变成同一个编码，这种"看起来不同、实际相同"的静默合并比允许两种写法更难排查。
 */
@Schema(description = "用户组保存入参")
public class UserGroupSaveRequest {

    @NotBlank(message = "用户组编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$",
            message = "用户组编码须以字母开头，仅含字母、数字、下划线，不超过 64 字")
    @Schema(description = "用户组编码，租户内唯一。创建后不可修改", example = "FIN_GROUP")
    private String groupCode;

    @NotBlank(message = "用户组名称不能为空")
    @Size(max = 128, message = "用户组名称不能超过 128 字")
    @Schema(description = "用户组名称", example = "财务部")
    private String groupName;

    @Schema(description = "租户主键，不传则归入当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "父用户组主键，顶级不传。**只用于列表分组展示，不产生权限** —— "
            + "判定链路里组不继承父组的角色")
    private Long parentId;

    @Size(max = 32, message = "分组方式不能超过 32 字")
    @Schema(description = "分组方式。**当前只支持 STATIC**（成员手工维护）："
            + "DYNAMIC 的动态成员求值尚未实现，传别的值返回 40001", example = "STATIC")
    private String category;

    @Schema(description = "是否默认用户组（新用户自动加入）：0 否 1 是，缺省 0。**当前只存不用**")
    private Integer isDefault;

    @Schema(description = "是否启用：0 否 1 是，缺省 1。**停用的用户组不再产生权限**")
    private Integer isEnabled;

    @Schema(description = "排序号，同级内升序；缺省 0")
    private Integer sortNo;

    @Size(max = 512, message = "描述不能超过 512 字")
    private String description;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
