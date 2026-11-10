package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档案文件引用实体，对应表 {@code archive_file}。
 *
 * <p>文件实体仍存放于 {@code whale-file}，本表只保存 {@code fileId} 与展示冗余字段；
 * {@code fileRole} 区分原件、附件与不可变快照。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_file")
public class ArchiveFile extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 档案条目ID */
    private Long itemId;

    /** 文件ID（whale-file 的 file_info.id） */
    private Long fileId;

    /** 文件角色：ORIGINAL / ATTACHMENT / SNAPSHOT */
    private String fileRole;

    /** 原始文件名（冗余） */
    private String originalName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 文件 MD5（冗余校验） */
    private String md5;

    /** 排序号 */
    private Integer sort;
}
