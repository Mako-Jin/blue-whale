package com.bluewhale.file.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件信息实体，对应表 {@code file_info}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code file_info} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfoPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 存储文件名（系统按 {@code objectKey} 尾段生成的唯一名称） */
    private String fileName;

    /** 原始文件名（用户上传时的文件名） */
    private String originalName;

    /** 对象存储桶名称 */
    private String bucket;

    /** 落盘时的存储插件类型（local/minio）；历史数据可为空 */
    private String storageType;

    /** 对象存储键（桶内完整路径，形如 {@code yyyy/MM/dd/<uuid>.<ext>}） */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件 MIME 类型，如 {@code application/pdf} */
    private String contentType;

    /** 文件 MD5 摘要（十六进制小写，用于去重/完整性校验） */
    private String md5;

    /** 状态：0 禁用 / 1 正常 / 2 归档锁定 */
    private Integer status;

    /** 上传用户 ID（逻辑关联 sys_user.id） */
    private Long uploaderId;

    /** 版本组根文件 ID；根版本为 {@code null} */
    private Long parentId;

    /** 版本号：根版本为 1，新版本依次递增 */
    private Integer version;

    /** 逻辑删除时间；恢复时置空（{@code deleted} 继承自 {@link BaseEntity}） */
    private LocalDateTime deleteTime;
}
