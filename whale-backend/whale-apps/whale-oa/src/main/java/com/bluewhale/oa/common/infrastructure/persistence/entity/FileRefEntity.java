package com.bluewhale.oa.common.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件引用实体，对应表 {@code oa_file_ref}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_file_ref")
public class FileRefEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 文件ID */
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 上传人用户ID */
    private Long uploadedBy;

    /** 用途 */
    private String purpose;
}
