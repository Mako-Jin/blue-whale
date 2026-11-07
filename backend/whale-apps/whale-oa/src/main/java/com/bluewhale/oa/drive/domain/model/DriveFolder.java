package com.bluewhale.oa.drive.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网盘文件夹聚合根。
 *
 * <p>{@code parentId = 0} 表示根目录；个人文件夹归属所有者，部门文件夹归属部门成员。</p>
 *
 * @author BlueWhale
 */
@Data
public class DriveFolder {

    /** 主键ID */
    private Long id;

    /** 父文件夹ID（0 表示根） */
    private Long parentId;

    /** 文件夹名称 */
    private String name;

    /** 范围：PERSONAL / DEPARTMENT */
    private String scope;

    /** 部门ID（部门文件夹生效） */
    private Long deptId;

    /** 所有者用户ID */
    private Long ownerId;

    /** 所有者姓名快照 */
    private String ownerName;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
