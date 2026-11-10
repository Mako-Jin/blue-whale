package com.bluewhale.oa.drive.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网盘文件登记实体，对应表 {@code oa_drive_file}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_drive_file")
public class DriveFileEntity extends BaseEntity {

    private Long folderId;

    private Long fileId;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private Long uploaderId;

    private String uploaderName;
}
