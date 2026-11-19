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
    @NotBlank(message = "{validation.forum.board.code-required}")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,63}$", message = "{validation.forum.board.code-pattern}")
    private String code;

    /** 版块名称 */
    @NotBlank(message = "{validation.forum.board.name-required}")
    @Size(max = 64, message = "{validation.forum.board.name-size}")
    private String name;

    /** 版块简介 */
    @Size(max = 512, message = "{validation.forum.board.description-size}")
    private String description;

    /** 图标标识 */
    @Size(max = 64, message = "{validation.forum.board.icon-size}")
    private String icon;

    /** 可见性：ALL / ROLE / DEPT */
    @Pattern(regexp = "^(ALL|ROLE|DEPT)$", message = "{validation.forum.board.visibility-pattern}")
    private String visibility = "ALL";

    /** 可见角色码，逗号分隔（visibility=ROLE 生效） */
    @Size(max = 512, message = "{validation.forum.board.visible-roles-size}")
    private String visibleRoles;

    /** 可见部门ID，逗号分隔（visibility=DEPT 生效） */
    @Size(max = 512, message = "{validation.forum.board.visible-depts-size}")
    private String visibleDepts;

    /** 审核模式：0 先发后审 / 1 先审后发 */
    @Min(value = 0, message = "{validation.forum.board.audit-mode-range}")
    @Max(value = 1, message = "{validation.forum.board.audit-mode-range}")
    private Integer auditMode = 0;

    /** 发帖策略：OPEN / READONLY / NOTICE_ONLY */
    @Pattern(regexp = "^(OPEN|READONLY|NOTICE_ONLY)$", message = "{validation.forum.board.post-policy-pattern}")
    private String postPolicy = "OPEN";

    /** 同级排序号 */
    private Integer sortNo = 0;

    /** 状态：0 停用 / 1 启用 */
    @Min(value = 0, message = "{validation.forum.board.status-range}")
    @Max(value = 1, message = "{validation.forum.board.status-range}")
    private Integer status = 1;
}
