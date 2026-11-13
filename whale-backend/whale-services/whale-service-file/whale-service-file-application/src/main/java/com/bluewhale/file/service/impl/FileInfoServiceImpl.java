package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAsset;
import com.bluewhale.file.domain.file.repository.FileAssetPage;
import com.bluewhale.file.domain.file.repository.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 文件信息服务实现
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileInfoServiceImpl implements FileInfoService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 对象键日期目录格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 对象键租户目录前缀（Wave 5.54 Phase 2：存储层按租户隔离） */
    private static final String TENANT_KEY_PREFIX = "tenants/";

    /** 原始文件名缺失时的占位名 */
    private static final String UNKNOWN_FILENAME = "unknown";

    /** 预签名默认有效期（秒） */
    private static final int DEFAULT_EXPIRES_SECONDS = 600;

    /** 预签名有效期上限（秒） */
    private static final int MAX_EXPIRES_SECONDS = 3600;

    /** 分片大小未配置或非法时的兜底值：5MB */
    private static final long DEFAULT_CHUNK_SIZE = 5242880L;

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final ChunkUploadStore chunkUploadStore;

    private final FileProperties fileProperties;

    @Override
    public FileUploadVO upload(UploadContent content, String bucket, Long uploaderId) {
        return upload(content, bucket, uploaderId, null);
    }

    @Override
    public FileUploadVO upload(UploadContent content, String bucket, Long uploaderId, UploadLimits limits) {
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        UploadLimits effective = effectiveLimits(limits);
        if (content.size() > effective.maxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        validateQuota(content.size(), uploaderId, effective.quotaBytes());
        byte[] bytes = content.content();
        return persistFile(new ByteArrayInputStream(bytes), content.originalName(), content.contentType(),
                bytes.length, DigestUtils.md5DigestAsHex(bytes), bucket, uploaderId, effective);
    }

    @Override
    public FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId) {
        return rapidUpload(request, uploaderId, null);
    }

    @Override
    public FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId, UploadLimits limits) {
        UploadLimits effective = effectiveLimits(limits);
        validateDeclaredSize(request.getSize(), effective);
        String md5 = request.getMd5().toLowerCase(Locale.ROOT);
        FileRapidUploadVO vo = new FileRapidUploadVO();
        FileAsset existing = fileAssetRepository.findReusableByDigest(md5, request.getSize()).orElse(null);
        if (existing == null) {
            vo.setHit(false);
            return vo;
        }
        validateQuota(request.getSize(), uploaderId, effective.quotaBytes());
        FileAsset asset = FileAsset.reuse(existing, resolveOriginalName(request.getFileName()),
                request.getContentType(), uploaderId);
        FileAsset saved = persistAsset(asset, effective);
        vo.setHit(true);
        vo.setFile(toUploadVo(saved));
        return vo;
    }

    @Override
    public FileQuotaVO quota(Long uploaderId) {
        return quota(uploaderId, null);
    }

    @Override
    public FileQuotaVO quota(Long uploaderId, UploadLimits limits) {
        FileQuotaVO vo = new FileQuotaVO();
        long quota = effectiveLimits(limits).quotaBytes();
        vo.setQuotaBytes(quota);
        if (uploaderId == null || quota <= 0) {
            vo.setUsedBytes(0L);
            vo.setRemainingBytes(0L);
            vo.setUnlimited(true);
            return vo;
        }
        long used = fileAssetRepository.sumNormalSizeByUploader(uploaderId);
        vo.setUsedBytes(used);
        vo.setRemainingBytes(Math.max(quota - used, 0L));
        vo.setUnlimited(false);
        return vo;
    }

    @Override
    public PageResult<FileInfoVO> page(FileQueryRequest request, Long requesterId, boolean canManage) {
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        // 无管理权限时仅可见本人上传的文件；服务间上传（uploaderId 为空）保持租户可见
        Long uploaderFilter = canManage ? null : requesterId;
        FileAssetQuery query = new FileAssetQuery(request.getOriginalName(), request.getBucket(),
                request.getCreateTimeStart(), request.getCreateTimeEnd(), uploaderFilter,
                request.getCurrent(), size);
        FileAssetPage page = fileAssetRepository.page(query);
        List<FileInfoVO> records = page.records().stream().map(FileInfoServiceImpl::toVo).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    public FileInfoVO getDetail(Long id, Long requesterId, boolean canManage) {
        return toVo(requireAccessibleFile(id, requesterId, canManage));
    }

    @Override
    public FileChangeVO getChangeDetail(Long id) {
        FileAsset asset = fileAssetRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        ensureTenantVisible(asset);
        return toChangeVo(asset);
    }

    @Override
    public InputStream openContent(Long id, Long requesterId, boolean canManage) {
        FileAsset asset = requireAccessibleFile(id, requesterId, canManage);
        return fileStorage.load(asset.getBucket(), asset.getObjectKey());
    }

    @Override
    public InputStream openContent(Long id, Long requesterId, boolean canManage, long offset, long length) {
        FileAsset asset = requireAccessibleFile(id, requesterId, canManage);
        return fileStorage.load(asset.getBucket(), asset.getObjectKey(), offset, length);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long requesterId, boolean canManage) {
        FileAsset asset = requireAccessibleFile(id, requesterId, canManage);
        asset.assertDeletable();
        fileAssetRepository.deleteById(id);
    }

    @Override
    public PageResult<FileChangeVO> changes(LocalDateTime since, long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        FileAssetPage page = fileAssetRepository.findChanges(since, pageCurrent, pageSize);
        List<FileChangeVO> records = page.records().stream().map(FileInfoServiceImpl::toChangeVo).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id) {
        FileAsset asset = requireFile(id);
        if (!asset.lock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        FileAsset asset = requireFile(id);
        if (!asset.unlock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    public FilePresignedVO presignedUrl(Long id, Integer expiresSeconds, Long requesterId, boolean canManage) {
        FileAsset asset = requireAccessibleFile(id, requesterId, canManage);
        int expires = resolveExpiresSeconds(expiresSeconds);
        FilePresignedVO vo = new FilePresignedVO();
        vo.setUrl(fileStorage.presignedDownloadUrl(asset.getBucket(), asset.getObjectKey(), expires));
        vo.setExpiresSeconds(expires);
        return vo;
    }

    @Override
    public ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request) {
        return initChunkUpload(request, null);
    }

    @Override
    public ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request, UploadLimits limits) {
        long size = request.getSize();
        UploadLimits effective = effectiveLimits(limits);
        validateDeclaredSize(size, effective);
        ChunkUploadSessionId sessionId = StringUtils.hasText(request.getUploadId())
                ? ChunkUploadSessionId.of(request.getUploadId())
                : ChunkUploadSessionId.random();
        // 同一会话重复 init 时保留既有分片，实现断点续传；生效上限随会话登记，
        // 供 complete 阶段与当前上限取更严格者，防止两阶段绕过
        chunkUploadStore.initialize(sessionId,
                new ChunkUploadMetadata(request.getFileName(), request.getContentType(), size,
                        effective.maxSizeBytes(), effective.quotaBytes()));
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId(sessionId.value());
        vo.setChunkSize(resolveChunkSize());
        vo.setUploadedIndexes(chunkUploadStore.uploadedPartIndexes(sessionId));
        return vo;
    }

    @Override
    public long chunkSizeBytes() {
        return resolveChunkSize();
    }

    @Override
    public List<Integer> uploadChunkPart(String uploadId, int index, UploadContent part) {
        if (part == null || part.isEmpty() || index < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ChunkUploadSessionId sessionId = requireSession(uploadId);
        if (part.size() > resolveChunkSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片大小超出上限");
        }
        chunkUploadStore.storePart(sessionId, index, new ByteArrayInputStream(part.content()), part.size());
        return chunkUploadStore.uploadedPartIndexes(sessionId);
    }

    @Override
    public List<Integer> chunkUploadStatus(String uploadId) {
        return chunkUploadStore.uploadedPartIndexes(requireSession(uploadId));
    }

    @Override
    public FileUploadVO completeChunkUpload(ChunkUploadCompleteRequest request, Long uploaderId) {
        return completeChunkUpload(request, uploaderId, null);
    }

    @Override
    public FileUploadVO completeChunkUpload(ChunkUploadCompleteRequest request, Long uploaderId,
                                            UploadLimits limits) {
        long size = request.getSize();
        ChunkUploadSessionId sessionId = requireSession(request.getUploadId());
        // complete 阶段同时校验「init 登记的生效上限」与「当前请求的上限」，任一阶段权限被回收都失败
        UploadLimits effective = UploadLimits.tighten(effectiveLimits(limits),
                recordedLimits(chunkUploadStore.metadata(sessionId)));
        validateDeclaredSize(size, effective);
        validateQuota(size, uploaderId, effective.quotaBytes());
        int expectedCount = expectedChunkCount(size);
        List<Integer> uploaded = chunkUploadStore.uploadedPartIndexes(sessionId);
        List<Integer> missing = IntStream.range(0, expectedCount)
                .filter(i -> !uploaded.contains(i))
                .boxed()
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少分片索引: " + missing);
        }
        if (uploaded.size() != expectedCount) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片数量与文件大小不匹配");
        }
        try (MergedChunkUpload merged = chunkUploadStore.merge(sessionId, expectedCount)) {
            if (merged.size() != size) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片总大小与声明大小不一致");
            }
            FileUploadVO vo = persistFile(merged.content(), request.getFileName(), merged.contentType(),
                    size, merged.md5(), request.getBucket(), uploaderId, effective);
            // 仅在合并落存储成功后清理暂存数据；失败时保留分片供客户端续传/重试
            chunkUploadStore.discard(sessionId);
            return vo;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 将已就绪的内容写入存储并登记元数据（普通上传与分片合并共用）。
     *
     * @param in 内容输入流，由调用方负责关闭
     * @param originalName 原始文件名
     * @param contentType MIME 类型，可为 {@code null}
     * @param fileSize 内容字节数
     * @param md5 内容 MD5 摘要（十六进制小写）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @param limits 生效的上传上限与配额
     * @return 上传结果视图对象
     * @throws BizException 大小超限时抛出 {@code PARAM_ERROR}
     */
    private FileUploadVO persistFile(InputStream in, String originalName, String contentType,
                                     long fileSize, String md5, String bucket, Long uploaderId,
                                     UploadLimits limits) {
        if (fileSize > limits.maxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小超出上限");
        }
        String name = resolveOriginalName(originalName);
        String resolvedBucket = StringUtils.hasText(bucket)
                ? bucket : fileProperties.getBucket().getDefault();
        String objectKey = buildObjectKey(name);
        fileStorage.store(resolvedBucket, objectKey, in);
        try {
            FileAsset asset = FileAsset.register(resolvedBucket, objectKey, name, fileSize, contentType, md5, uploaderId);
            return toUploadVo(persistAsset(asset, limits));
        } catch (RuntimeException e) {
            // 落库失败（含配额超限）：补偿删除已写入的物理对象，避免产生孤儿文件
            deleteStoredQuietly(resolvedBucket, objectKey);
            throw e;
        }
    }

    /**
     * 在个人配额内原子登记文件元数据（配额统计与写入处于同一临界区，避免并发穿透）
     *
     * @param asset 待登记的文件资产聚合根
     * @param limits 生效的上传上限与配额
     * @return 回填主键后的文件资产聚合根
     * @throws BizException 超出配额时抛出 {@code PARAM_ERROR}
     */
    private FileAsset persistAsset(FileAsset asset, UploadLimits limits) {
        return fileAssetRepository.saveWithQuotaCheck(asset, limits.quotaBytes());
    }

    /**
     * 尽力删除已写入存储的物理对象：补偿失败仅告警，不掩盖原始业务异常
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储键
     */
    private void deleteStoredQuietly(String bucket, String objectKey) {
        try {
            fileStorage.delete(bucket, objectKey);
        } catch (RuntimeException e) {
            log.warn("回滚已写入的文件对象失败，可能残留孤儿文件: bucket={}, objectKey={}", bucket, objectKey, e);
        }
    }

    /**
     * 查询文件资产，不存在或跨租户时抛出 {@code NOT_FOUND}
     *
     * @param id 文件ID
     * @return 文件资产聚合根
     */
    private FileAsset requireFile(Long id) {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        ensureTenantVisible(asset);
        return asset;
    }

    /**
     * 查询文件资产并校验调用者可见性：不存在、跨租户或跨用户越权均按 {@code NOT_FOUND} 处理，
     * 避免泄露文件是否存在。
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 文件资产聚合根
     * @throws BizException 不可见时抛出 {@code NOT_FOUND}
     */
    private FileAsset requireAccessibleFile(Long id, Long requesterId, boolean canManage) {
        FileAsset asset = requireFile(id);
        ensureAccessible(asset, requesterId, canManage);
        return asset;
    }

    /**
     * 校验文件归属：管理权限可访问全租户文件；上传者本人可访问；服务间上传（上传者为空）
     * 维持租户内可见。
     *
     * @param asset 文件资产聚合根
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @throws BizException 越权访问时抛出 {@code NOT_FOUND}
     */
    private static void ensureAccessible(FileAsset asset, Long requesterId, boolean canManage) {
        if (canManage || asset.getUploaderId() == null
                || Objects.equals(asset.getUploaderId(), requesterId)) {
            return;
        }
        throw new BizException(ErrorCode.NOT_FOUND);
    }

    /**
     * 校验文件资产对当前租户可见，跨租户按不存在处理以避免泄露文件是否存在
     *
     * @param asset 文件资产聚合根
     * @throws BizException 跨租户访问时抛出 {@code NOT_FOUND}
     */
    private static void ensureTenantVisible(FileAsset asset) {
        if (!asset.isVisibleToTenant(TenantContext.get())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * 解析预签名有效期：非正数取默认值，超过上限则截断
     *
     * @param expiresSeconds 请求的有效期（秒），可为 {@code null}
     * @return 生效的有效期（秒）
     */
    private static int resolveExpiresSeconds(Integer expiresSeconds) {
        if (expiresSeconds == null || expiresSeconds <= 0) {
            return DEFAULT_EXPIRES_SECONDS;
        }
        return Math.min(expiresSeconds, MAX_EXPIRES_SECONDS);
    }

    /**
     * 解析生效上传上限：调用方未传入（服务间调用等）时回落全局默认值
     *
     * @param limits 调用方按权限码解析出的上限，可为 {@code null}
     * @return 生效上传上限，非 {@code null}
     */
    private UploadLimits effectiveLimits(UploadLimits limits) {
        return limits == null
                ? new UploadLimits(fileProperties.getMaxSizeBytes(), fileProperties.getQuotaBytes())
                : limits;
    }

    /**
     * 把分片会话中登记的生效上限转换为上传上限
     *
     * @param metadata 会话元数据，可为 {@code null}（会话刚创建或未登记上限）
     * @return 会话上限；未登记时返回 {@code null}（表示不额外收紧）
     */
    private static UploadLimits recordedLimits(ChunkUploadMetadata metadata) {
        if (metadata == null || !metadata.hasRecordedLimits()) {
            return null;
        }
        return new UploadLimits(metadata.maxSizeBytes(), metadata.quotaBytes());
    }

    /**
     * 校验声明的文件大小：必须大于 0 且不超过生效上限
     *
     * @param size 声明的文件字节数
     * @param limits 生效的上传上限
     * @throws BizException 大小非法时抛出 {@code PARAM_ERROR}
     */
    private static void validateDeclaredSize(Long size, UploadLimits limits) {
        if (size == null || size <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小必须大于 0");
        }
        if (size > limits.maxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小超出上限");
        }
    }

    /**
     * 乐观预检个人存储配额：不限配额或无用户态时直接放行，否则提前拒绝明显超配额的写入。
     *
     * <p>并发安全由仓储端口 {@code saveWithQuotaCheck} 的临界区保证，本方法仅用于快速失败、
     * 避免无谓的存储写入。</p>
     *
     * @param incomingSize 本次拟写入的字节数
     * @param uploaderId 上传用户 ID；无登录态时为 {@code null}
     * @param quota 生效的个人配额；{@code <=0} 表示不限
     * @throws BizException 超出个人存储配额时抛出 {@code PARAM_ERROR}
     */
    private void validateQuota(long incomingSize, Long uploaderId, long quota) {
        if (quota <= 0 || uploaderId == null) {
            return;
        }
        long used = fileAssetRepository.sumNormalSizeByUploader(uploaderId);
        if (used + incomingSize > quota) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额");
        }
    }

    /**
     * 解析分片大小，配置非法时回落默认 5MB
     *
     * @return 分片大小（字节）
     */
    private long resolveChunkSize() {
        long configured = fileProperties.getChunkSizeBytes();
        return configured > 0 ? configured : DEFAULT_CHUNK_SIZE;
    }

    /**
     * 计算声明大小对应的分片数量（向上取整）
     *
     * @param size 文件总字节数
     * @return 期望的分片数量
     */
    private int expectedChunkCount(long size) {
        long chunkSize = resolveChunkSize();
        return (int) ((size + chunkSize - 1) / chunkSize);
    }

    /**
     * 解析并校验上传会话标识，会话不存在时抛出参数错误
     *
     * @param uploadId 调用方传入的 uploadId
     * @return 已校验的分片上传会话标识
     * @throws BizException uploadId 非法或会话不存在时抛出 {@code PARAM_ERROR}
     */
    private ChunkUploadSessionId requireSession(String uploadId) {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.of(uploadId);
        if (!chunkUploadStore.exists(sessionId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上传会话不存在或已失效");
        }
        return sessionId;
    }

    /**
     * 解析原始文件名，缺失时回落占位名
     *
     * @param rawName 上传文件原始名
     * @return 生效的原始文件名
     */
    private static String resolveOriginalName(String rawName) {
        return StringUtils.hasText(rawName) ? rawName : UNKNOWN_FILENAME;
    }

    /**
     * 生成对象键：{@code tenants/{tenantId}/yyyy/MM/dd/<uuid>.<ext>}
     *
     * @param originalName 原始文件名（用于提取扩展名）
     * @return 对象键
     */
    private static String buildObjectKey(String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName);
        String suffix = StringUtils.hasText(extension) ? "." + extension : "";
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return TENANT_KEY_PREFIX + TenantContext.segment() + "/"
                + LocalDate.now().format(DATE_FORMATTER) + "/" + uuid + suffix;
    }

    /**
     * 文件资产转上传结果视图对象
     *
     * @param asset 文件资产聚合根
     * @return 上传结果视图对象
     */
    private static FileUploadVO toUploadVo(FileAsset asset) {
        FileUploadVO vo = new FileUploadVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        return vo;
    }

    /**
     * 文件资产转信息视图对象
     *
     * @param asset 文件资产聚合根
     * @return 文件信息视图对象
     */
    private static FileInfoVO toVo(FileAsset asset) {
        FileInfoVO vo = new FileInfoVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        vo.setUploaderId(asset.getUploaderId());
        vo.setCreateTime(asset.getCreateTime());
        return vo;
    }

    /**
     * 文件资产转变更流视图对象
     *
     * @param asset 文件资产聚合根
     * @return 文件变更视图对象
     */
    private static FileChangeVO toChangeVo(FileAsset asset) {
        FileChangeVO vo = new FileChangeVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        vo.setStatus(asset.getStatus().getCode());
        vo.setUploaderId(asset.getUploaderId());
        vo.setDeleted(asset.isDeleted() ? 1 : 0);
        vo.setCreateTime(asset.getCreateTime());
        vo.setUpdateTime(asset.getUpdateTime());
        return vo;
    }
}
