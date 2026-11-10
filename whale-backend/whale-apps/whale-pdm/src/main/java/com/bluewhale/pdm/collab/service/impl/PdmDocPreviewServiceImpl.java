package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.entity.PdmDocPreviewJob;
import com.bluewhale.pdm.collab.mapper.PdmDocPreviewJobMapper;
import com.bluewhale.pdm.collab.service.PdmDocPreviewService;
import com.bluewhale.pdm.collab.support.PdmDocPreviewRules;
import com.bluewhale.pdm.collab.support.PdmDocPreviewStatus;
import com.bluewhale.pdm.collab.vo.PdmDocPreviewJobVO;
import com.bluewhale.pdm.document.client.PdmDocFileClient;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 在线预览服务实现
 *
 * <p>幂等键为 {@code documentId:versionId:previewType}；原生格式直接成功，
 * 需外部转换的格式标记为 UNSUPPORTED 并记录可诊断原因。所有读取与内容下载
 * 复用图文档服务的 ACL 校验，不绕过文档权限。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PdmDocPreviewServiceImpl implements PdmDocPreviewService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final PdmDocPreviewJobMapper jobMapper;

    private final PdmDocDocumentService documentService;

    private final PdmDocFileClient fileClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocPreviewJobVO request(Long documentId, Long versionId, String previewType,
                                      PdmDocViewer viewer) {
        String type = previewType == null ? "" : previewType.trim().toUpperCase();
        if (!PdmDocPreviewStatus.isValidType(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "预览类型仅支持 2D/3D");
        }
        PdmDocVersionVO version = requireVersion(documentId, versionId, viewer);
        String idempotencyKey = documentId + ":" + versionId + ":" + type;
        PdmDocPreviewJob existing = jobMapper.selectOne(new LambdaQueryWrapper<PdmDocPreviewJob>()
                .eq(PdmDocPreviewJob::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return toVo(existing);
        }
        PdmDocPreviewJob job = new PdmDocPreviewJob();
        job.setDocumentId(documentId);
        job.setVersionId(versionId);
        job.setVersionNo(version.getVersionNo());
        job.setPreviewType(type);
        job.setSourceFileName(version.getFileName());
        job.setSourceContentType(version.getContentType());
        job.setRequestedBy(viewer == null ? null : viewer.userId());
        job.setIdempotencyKey(idempotencyKey);
        job.setRetryCount(0);
        evaluate(job);
        jobMapper.insert(job);
        return toVo(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocPreviewJobVO retry(Long jobId, PdmDocViewer viewer) {
        PdmDocPreviewJob job = requireJob(jobId);
        if (PdmDocPreviewStatus.SUCCESS.equals(job.getStatus())) {
            return toVo(job);
        }
        requireVersion(job.getDocumentId(), job.getVersionId(), viewer);
        job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
        evaluate(job);
        jobMapper.updateById(job);
        return toVo(job);
    }

    @Override
    public PageResult<PdmDocPreviewJobVO> page(Long documentId, Long versionId, long current, long size) {
        LambdaQueryWrapper<PdmDocPreviewJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(documentId != null, PdmDocPreviewJob::getDocumentId, documentId);
        wrapper.eq(versionId != null, PdmDocPreviewJob::getVersionId, versionId);
        wrapper.orderByDesc(PdmDocPreviewJob::getCreateTime).orderByDesc(PdmDocPreviewJob::getId);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<PdmDocPreviewJob> page =
                jobMapper.selectPage(new Page<>(Math.max(current, 1L), pageSize), wrapper);
        List<PdmDocPreviewJobVO> records = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmDocDownload content(Long jobId, PdmDocViewer viewer) {
        PdmDocPreviewJob job = requireJob(jobId);
        if (!PdmDocPreviewStatus.SUCCESS.equals(job.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "预览尚未就绪：" + job.getLastError());
        }
        PdmDocDownload original = documentService.download(job.getDocumentId(), job.getVersionId(), viewer);
        if (job.getArtifactFileId() == null) {
            return original;
        }
        byte[] artifact = fileClient.content(job.getArtifactFileId());
        return new PdmDocDownload(artifact, job.getSourceFileName(),
                job.getArtifactContentType() != null ? job.getArtifactContentType() : original.contentType());
    }

    /**
     * 按文件信息判定并填充任务结果
     *
     * @param job 预览任务
     */
    private void evaluate(PdmDocPreviewJob job) {
        LocalDateTime now = LocalDateTime.now();
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate(job.getPreviewType(), job.getSourceFileName(),
                        job.getSourceContentType());
        job.setStatus(decision.status());
        job.setTargetFormat(decision.targetFormat());
        job.setViewerHint(decision.viewerHint());
        job.setLastError(decision.message());
        job.setStartedAt(now);
        job.setFinishedAt(PdmDocPreviewStatus.SUCCESS.equals(decision.status()) ? now : null);
    }

    /**
     * 校验文档版本存在并具备查看权限
     *
     * @param documentId 文档 ID
     * @param versionId  版本 ID
     * @param viewer     访问者
     * @return 版本视图
     */
    private PdmDocVersionVO requireVersion(Long documentId, Long versionId, PdmDocViewer viewer) {
        List<PdmDocVersionVO> versions = documentService.versions(documentId, viewer);
        return versions.stream()
                .filter(version -> version.getId().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "文档版本不存在"));
    }

    /**
     * 要求预览任务存在
     *
     * @param jobId 任务 ID
     * @return 任务实体
     */
    private PdmDocPreviewJob requireJob(Long jobId) {
        PdmDocPreviewJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预览任务不存在");
        }
        return job;
    }

    /**
     * 实体转视图
     *
     * @param entity 预览任务
     * @return 视图
     */
    private PdmDocPreviewJobVO toVo(PdmDocPreviewJob entity) {
        PdmDocPreviewJobVO vo = new PdmDocPreviewJobVO();
        vo.setId(entity.getId());
        vo.setDocumentId(entity.getDocumentId());
        vo.setVersionId(entity.getVersionId());
        vo.setVersionNo(entity.getVersionNo());
        vo.setPreviewType(entity.getPreviewType());
        vo.setSourceFileName(entity.getSourceFileName());
        vo.setSourceContentType(entity.getSourceContentType());
        vo.setTargetFormat(entity.getTargetFormat());
        vo.setViewerHint(entity.getViewerHint());
        vo.setStatus(entity.getStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setLastError(entity.getLastError());
        vo.setArtifactFileId(entity.getArtifactFileId());
        vo.setArtifactContentType(entity.getArtifactContentType());
        vo.setArtifactSize(entity.getArtifactSize());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setRequestedBy(entity.getRequestedBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
