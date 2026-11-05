package com.bluewhale.pdm.collab.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线预览任务视图
 *
 * @author BlueWhale
 */
@Data
public class PdmDocPreviewJobVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 文档ID */
    private Long documentId;

    /** 版本ID */
    private Long versionId;

    /** 版本号 */
    private String versionNo;

    /** 预览类型 */
    private String previewType;

    /** 源文件名 */
    private String sourceFileName;

    /** 源内容类型 */
    private String sourceContentType;

    /** 目标格式 */
    private String targetFormat;

    /** 查看器提示 */
    private String viewerHint;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 产物 fileId */
    private Long artifactFileId;

    /** 产物内容类型 */
    private String artifactContentType;

    /** 产物大小 */
    private Long artifactSize;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 请求人 */
    private Long requestedBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
