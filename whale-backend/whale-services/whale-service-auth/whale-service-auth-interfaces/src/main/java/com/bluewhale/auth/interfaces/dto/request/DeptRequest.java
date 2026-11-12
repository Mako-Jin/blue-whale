package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/更新部门请求
 *
 * @author BlueWhale
 */
@Data
public class DeptRequest {

    /** 父部门ID，0 表示顶级 */
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过 64")
    private String deptName;

    private Integer sort;
    private String leader;
    /** 负责人用户 ID（逻辑关联 sys_user.id，允许为空） */
    private Long leaderUserId;
}
