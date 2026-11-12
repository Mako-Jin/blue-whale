package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门树节点响应
 *
 * @author BlueWhale
 */
@Data
public class DeptResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String deptName;
    private Integer sort;
    private String leader;
    /** 负责人用户 ID（逻辑关联 sys_user.id，允许为空） */
    private Long leaderUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<DeptResponse> children;
}
