package com.bluewhale.pdm.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 在线预览任务
 *
 * <p>按 {@code documentId:versionId:previewType} 幂等；原生格式（PDF/图片/STL/glTF）
 * 直接标记成功并由前端查看器读取原文件，需转换的格式在未配置转换服务时标记为不支持。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_doc_preview_job")
public class PdmDocPreviewJob extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 版本ID */
    private Long versionId;

    /** 版本号快照 */
    private String versionNo;

    /** 预览类型：2D/3D */
    private String previewType;

    /** 源文件名 */
    private String sourceFileName;

    /** 源内容类型 */
    private String sourceContentType;

    /** 目标格式：ORIGINAL/PDF/GLTF */
    private String targetFormat;

    /** 前端查看器提示：PDF/IMAGE/STL/GLTF */
    private String viewerHint;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED/UNSUPPORTED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 预览产物 fileId（原生格式为空） */
    private Long artifactFileId;

    /** 预览产物内容类型 */
    private String artifactContentType;

    /** 预览产物大小 */
    private Long artifactSize;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 请求人 */
    private Long requestedBy;

    /** 幂等键（documentId:versionId:previewType） */
    private String idempotencyKey;
}
