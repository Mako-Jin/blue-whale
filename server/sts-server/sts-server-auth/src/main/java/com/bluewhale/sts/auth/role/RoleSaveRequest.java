package com.bluewhale.sts.auth.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 角色新增 / 修改入参。
 *
 * <p><b>校验密度是分层的，不是"能加就加"：</b>
 * <ul>
 *   <li><b>有判定语义的字段严格校验</b> —— {@code status} 走 {@code Status} 枚举、
 *       {@code dataScope} 走 {@code DataScope} 枚举、{@code isDefault} 只接受 0/1。
 *       理由：这些值会被判定链路读取，写错不会报错，只会静默地改变行为
 *       （停用的角色还在产生权限、数据范围写成了不存在的档位）。
 *       <b>这里有一条订正：</b>{@code isDefault} 当初是被"顺带列进"这一类的 ——
 *       它当时没有读取方，所以这条理由在它身上本来是假的
 *       （同一时间 {@code RoleVO} 的另一份说明写着"当前只存不用"）。
 *       建号自动授予落地之后这句话才真的成立，见 ADR-0049。</li>
 *   <li><b>只存不用的字段宽松接受</b> —— {@code category} / {@code inheritStrategy} /
 *       {@code filterJson} / {@code roleLevel} 当前都没有消费方：角色继承未实现，
 *       而 {@code filterJson} 只在 {@code CUSTOM} 档被读、该档的解析本仓也没做
 *       （{@code dataScope} 本身是另一回事 —— 它**已参与求值**）。
 *       给它们编一套取值约束，等于提前定义一个还没有用例的契约 ——
 *       与 {@code resourceVersion} 的"只存不用"是同一种处理。</li>
 * </ul>
 * 这条分界线的依据是"写错了会不会有人受伤"，而不是字段的重要性。
 *
 * <p>{@code roleCode} 的形状用注解而不是服务层 if，理由同 {@code ResourceSaveRequest}：
 * 注解会进 OpenAPI 契约，前端能在提交前拦下一半错误。房内约定是全大写
 * {@code UPPER_SNAKE}（如 {@code SUPER_ADMIN}），但**不强制大小写** ——
 * 强制转换会让 {@code super_admin} 与 {@code SUPER_ADMIN} 变成同一个编码，
 * 而这种"看起来不同、实际相同"的静默合并比允许两种写法更难排查。
 */
@Schema(description = "角色保存入参")
public class RoleSaveRequest {

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$",
            message = "角色编码须以字母开头，仅含字母、数字、下划线，不超过 64 字")
    @Schema(description = "角色编码，租户内唯一。创建后不可修改。约定全大写，如 SUPER_ADMIN",
            example = "DEPT_MANAGER")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称不能超过 128 字")
    private String roleName;

    @Schema(description = "租户主键，不传则归入当前登录用户所属租户")
    private Long tenantId;

    @Schema(description = "父角色主键，顶级不传。**角色继承当前未实现**，此列只建层级")
    private Long parentId;

    @Schema(description = "角色层级，顶级为 1；缺省按父角色 +1，无父则为 1")
    private Integer roleLevel;

    @Size(max = 32, message = "角色分类不能超过 32 字")
    @Schema(description = "角色分类，如 SYSTEM / BUSINESS / CUSTOM。仅用于分组展示")
    private String category;

    @Schema(description = "数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM，缺省 ALL。"
            + "CUSTOM 需要 filterJson，而本仓未实现其解析（求值时 precise = false），"
            + "管理界面因此不提供这一档",
            example = "ALL")
    private String dataScope;

    @Size(max = 4096, message = "自定义数据范围条件过长")
    @Schema(description = "自定义数据范围过滤条件（JSON），仅 dataScope = CUSTOM 时使用。"
            + "**本仓未实现其解析** —— CUSTOM 档求值结果为 precise = false，"
            + "已接入的查询保守地按「仅本人」处理")
    private String filterJson;

    @Size(max = 32, message = "继承策略不能超过 32 字")
    @Schema(description = "继承策略：NONE / INHERIT / OVERRIDE。**当前只存不用**")
    private String inheritStrategy;

    @Schema(description = "是否默认角色（新用户自动授予）：0 否 1 是，缺省 0。"
            + "**已生效** —— 新建用户时按本租户全部为 1、且状态为 ENABLED 的角色自动授予，"
            + "见 ADR-0049")
    private Integer isDefault;

    @Schema(description = "排序号，同级内升序；缺省 0")
    private Integer sortNo;

    @Schema(description = "状态：ENABLED / DISABLED，缺省 ENABLED。停用的角色不再产生权限")
    private String status;

    @Size(max = 512, message = "描述不能超过 512 字")
    private String description;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
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

    public Integer getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(Integer roleLevel) {
        this.roleLevel = roleLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getInheritStrategy() {
        return inheritStrategy;
    }

    public void setInheritStrategy(String inheritStrategy) {
        this.inheritStrategy = inheritStrategy;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
