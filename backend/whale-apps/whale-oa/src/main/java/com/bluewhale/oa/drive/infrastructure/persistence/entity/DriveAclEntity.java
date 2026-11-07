package com.bluewhale.oa.drive.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网盘授权实体，对应表 {@code oa_drive_acl}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_drive_acl")
public class DriveAclEntity extends BaseEntity {

    private Long folderId;

    private String subjectType;

    private Long subjectId;

    private String subjectName;

    private String effect;

    private String permission;
}
