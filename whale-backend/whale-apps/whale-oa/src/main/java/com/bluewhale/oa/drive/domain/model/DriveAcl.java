package com.bluewhale.oa.drive.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网盘文件夹授权记录。
 *
 * <p>授权主体为用户或部门，效果为允许或拒绝；拒绝优先于允许。</p>
 *
 * @author BlueWhale
 */
@Data
public class DriveAcl {

    /** 主键ID */
    private Long id;

    /** 文件夹ID */
    private Long folderId;

    /** 授权主体类型：USER / DEPT */
    private String subjectType;

    /** 授权主体ID（用户ID或部门ID） */
    private Long subjectId;

    /** 授权主体名称快照 */
    private String subjectName;

    /** 效果：ALLOW / DENY */
    private String effect;

    /** 权限：READ / WRITE / MANAGE */
    private String permission;

    /** 创建时间 */
    private LocalDateTime createTime;
}
