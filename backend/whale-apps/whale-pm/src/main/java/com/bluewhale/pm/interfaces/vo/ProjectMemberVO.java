package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目成员视图对象
 *
 * @author BlueWhale
 */
@Data
public class ProjectMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 成员用户ID */
    private String userId;

    /** 项目角色：ADMIN / MEMBER / VIEWER */
    private String projectRole;

    /** 加入时间 */
    private LocalDateTime joinedTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
