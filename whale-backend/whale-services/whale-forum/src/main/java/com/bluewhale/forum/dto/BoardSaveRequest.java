package com.bluewhale.forum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 版块保存请求（新建/更新共用）。
 *
 * <p>{@code code} 只在新建时生效，更新不允许改编码——编码是版块对外的稳定标识，
 * 改了会让已收藏/已授权的引用失效。</p>
 *
 * @author BlueWhale
 */
@Data
public class BoardSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上级版块ID（null 为根/大区） */
    private Long parentId;

    /** 版块编码（租户内唯一，小写字母/数字/下划线/短横线） */
    @NotBlank(message = "版块编码不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,63}$", message = "版块编码只能是小写字母、数字、下划线或短横线（2-64 位）")
    private String code;

    /** 版块名称 */
    @NotBlank(message = "版块名称不能为空")
    @Size(max = 64, message = "版块名称不超过 64 字符")
    private String name;

    /** 版块简介 */
    @Size(max = 512, message = "版块简介不超过 512 字符")
    private String description;

    /** 图标标识 */
    @Size(max = 64, message = "图标标识不超过 64 字符")
    private String icon;

    /** 可见性：ALL / ROLE / DEPT */
    @Pattern(regexp = "^(ALL|ROLE|DEPT)$", message = "可见性只能是 ALL / ROLE / DEPT")
    private String visibility = "ALL";

    /** 可见角色码，逗号分隔（visibility=ROLE 生效） */
    @Size(max = 512, message = "可见角色不超过 512 字符")
    private String visibleRoles;

    /** 可见部门ID，逗号分隔（visibility=DEPT 生效） */
    @Size(max = 512, message = "可见部门不超过 512 字符")
    private String visibleDepts;

    /** 审核模式：0 先发后审 / 1 先审后发 */
    @Min(value = 0, message = "审核模式只能是 0 或 1")
    @Max(value = 1, message = "审核模式只能是 0 或 1")
    private Integer auditMode = 0;

    /** 发帖策略：OPEN / READONLY / NOTICE_ONLY */
    @Pattern(regexp = "^(OPEN|READONLY|NOTICE_ONLY)$", message = "发帖策略只能是 OPEN / READONLY / NOTICE_ONLY")
    private String postPolicy = "OPEN";

    /** 同级排序号 */
    private Integer sortNo = 0;

    /** 状态：0 停用 / 1 启用 */
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status = 1;
}
