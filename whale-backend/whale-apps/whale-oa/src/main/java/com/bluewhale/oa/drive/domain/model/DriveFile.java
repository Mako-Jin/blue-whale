package com.bluewhale.oa.drive.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网盘文件登记记录。
 *
 * <p>文件本体由 whale-file 托管，本记录仅保存引用与归属快照。</p>
 *
 * @author BlueWhale
 */
@Data
public class DriveFile {

    /** 主键ID */
    private Long id;

    /** 所属文件夹ID */
    private Long folderId;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名快照 */
    private String fileName;

    /** 文件字节数快照 */
    private Long fileSize;

    /** MIME 类型快照 */
    private String contentType;

    /** 上传人用户ID */
    private Long uploaderId;

    /** 上传人姓名快照 */
    private String uploaderName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
