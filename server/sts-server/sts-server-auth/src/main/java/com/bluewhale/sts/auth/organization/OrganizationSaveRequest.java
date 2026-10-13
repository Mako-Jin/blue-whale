package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 组织新增 / 修改入参。
 *
 * <p><b>{@code parentId} 在修改路径上会被显式拒绝（返回 40001），移动要走
 * {@code PUT /api/auth/orgs/{id}/parent}。</b>这不是形式主义：移动一棵子树会重写
 * 整棵子树的 {@code code_path} 与 {@code level}，而数据范围的 {@code DEPT_AND_CHILD}
 * 正是按路径前缀求值的 —— 也就是说，一次"换个上级部门"会**同时改变子树上每个人的可见范围**。
 * 它有自己的审计动作（{@code auth.org.move}），混进一次普通字段更新里就再也查不出来。
 * 拒绝而不是静默忽略，是因为静默忽略的表现是"保存成功、层级没变"，
 * 调用方会以为改好了。
 *
 * <p><b>三列由服务端派生，一律不接受调用方指定：</b>
 * <ul>
 *   <li>{@code orgLevel} 与 {@code codePath} —— 它们必须与 {@code parentId} 一致。
 *       让调用方传，就会出现"挂在这棵树下、路径却指向另一棵"的节点，
 *       而 {@code DEPT_AND_CHILD} 按路径求值，这种节点的子树会**静默地**算不进任何人的范围；</li>
 *   <li>{@code fullName}（组织全称）—— 按层级拼出来（{@code 蓝鲸集团/研发部/前端组}），
 *       改名与移动时整棵子树一起重写。它是 {@code orgName} 在树上的确定函数，
 *       由调用方提供就成了一份**可能与树不一致的副本**。</li>
 * </ul>
 *
 * <p><b>{@code managerName} 同样不入参。</b>调用方只给 {@code managerId}，
 * 姓名由服务端从用户表取 —— 理由同上：给两份就必然出现两份不一致，
 * 而列表展示用的是姓名、点开详情用的是 id，不一致时两个界面会各说各话。
 *
 * <p><b>{@code orgType} 是唯一"存但不用"的字段，这是有意的、且与
 * {@code UserGroupSaveRequest} 拒绝 {@code filterJson} 并不矛盾。</b>
 * {@code filterJson} 被拒绝是因为它**看起来会改变行为**（填了过滤条件、成员一个没变）；
 * 而 {@code orgType}（{@code COMPANY} / {@code DEPT} / {@code TEAM}）是一句描述，
 * 没有任何地方读它、界面也不承诺它会做什么。当前**不等于**枚举，是因为还没有
 * 消费方 —— 等真有地方按类型分叉（例如菜单可见粒度），它才该被收窄成枚举。
 *
 * <p>{@code status} 只接受 {@code ENABLED} / {@code DISABLED}（服务层校验），
 * 它是**真开关**：被停用的组织不再能作为 DEPT 档位的范围目标（见
 * {@code OrganizationService} 与 {@code DataScopeService}）。
 * <b>停用不级联</b> —— 与角色、用户组一致：停用上级部门不影响下级各自的有效性。
 */
@Schema(description = "组织保存入参")
public class OrganizationSaveRequest {

    @NotBlank(message = "组织编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$",
            message = "组织编码须以字母开头，仅含字母、数字、下划线，不超过 64 字")
    @Schema(description = "组织编码，租户内唯一。**创建后不可修改**（修改路径不读这个字段）",
            example = "RD_FE")
    private String orgCode;

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 128, message = "组织名称不能超过 128 字")
    @Schema(description = "组织名称", example = "前端组")
    private String orgName;

    @Schema(description = "租户主键，不传则归入当前登录用户所属租户。**修改路径不读这个字段** —— "
            + "把组织搬到另一个租户会让它连同其子树瞬间与所有成员归属错位，要变就新建")
    private Long tenantId;

    @Schema(description = "父组织主键，顶级不传。**只在新增时生效**：修改时会因为"
            + "「值与原值不同」而被拒绝（40001），移动请用 PUT /api/auth/orgs/{id}/parent")
    private Long parentId;

    @Size(max = 32, message = "组织类型不能超过 32 字")
    @Schema(description = "组织类型（COMPANY / DEPT / TEAM …），**只作展示、不参与任何判定**",
            example = "DEPT")
    private String orgType;

    @Schema(description = "负责人用户主键。姓名由服务端从用户表取，入参不接受 managerName")
    private Long managerId;

    @Schema(description = "排序号，同级内升序；缺省 0")
    private Integer sortNo;

    @Size(max = 16, message = "状态不能超过 16 字")
    @Schema(description = "状态：ENABLED / DISABLED，缺省 ENABLED。**停用的组织不再能作为"
            + " DEPT 档位的范围目标**；停用不级联，不影响它的下级", example = "ENABLED")
    private String status;

    @Size(max = 512, message = "描述不能超过 512 字")
    private String description;

    @Size(max = 128, message = "联系人不能超过 128 字")
    private String contactName;

    @Size(max = 32, message = "联系电话不能超过 32 字")
    private String contactPhone;

    @Size(max = 128, message = "联系邮箱不能超过 128 字")
    private String contactEmail;

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
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

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

}
