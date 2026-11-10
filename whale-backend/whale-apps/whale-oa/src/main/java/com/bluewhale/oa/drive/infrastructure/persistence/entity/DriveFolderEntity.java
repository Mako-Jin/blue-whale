package com.bluewhale.oa.drive.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网盘文件夹实体，对应表 {@code oa_drive_folder}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_drive_folder")
public class DriveFolderEntity extends BaseEntity {

    private Long parentId;

    private String name;

    private String scope;

    private Long deptId;

    private Long ownerId;

    private String ownerName;

    private Integer sortNo;
}
