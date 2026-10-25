package com.bluewhale.agent.support.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件服务内网变更流记录（知识库采集入参）。
 *
 * @author BlueWhale
 */
@Data
public class RemoteFileChange {

    /** 文件ID */
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 对象存储桶 */
    private String bucket;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** MD5 摘要 */
    private String md5;

    /** 状态：1 正常 / 0 禁用 / 2 归档锁定 */
    private Integer status;

    /** 上传用户ID */
    private Long uploaderId;

    /** 逻辑删除：0 未删除 / 1 已删除 */
    private Integer deleted;

    /** 更新时间（增量游标） */
    private LocalDateTime updateTime;

    /**
     * 判断文件是否仍可入库。
     *
     * @return 未删除、未禁用返回 {@code true}
     */
    public boolean collectable() {
        return (deleted == null || deleted == 0) && status != null && status != 0;
    }
}
