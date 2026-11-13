package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadCompleteCommand;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FilePresignedResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.service.FileInfoService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 文件信息服务实现
 *
 * <p><b>事务边界</b>：写库事务由仓储端口保证——{@code saveWithQuotaCheck} 自带
 * {@code REQUIRES_NEW} 事务，配额统计与写入处于同一临界区；外部存储写入不参与数据库事务，
 * 落库失败时通过补偿删除物理对象保证最终一致。不将存储写包进事务，是因为对象存储属非事务资源，
 * 数据库回滚无法撤销已写入的物理对象，包进事务只会在回滚后留下孤儿对象。仅在需要跨多次仓储调用
 * 保持原子性的方法（{@code delete}/{@code lock}/{@code unlock}）上声明应用层事务。</p>
 *
 * <p><b>约束</b>：{@code upload}/{@code completeChunkUpload} 不得声明或被外层事务包裹——
 * {@code saveWithQuotaCheck} 的独立事务提交后不随外层回滚，若存在外层事务，其失败时元数据已落库
 * 而补偿逻辑无法撤销，破坏「存储写入 + 元数据登记」的一致性。</p>
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
    public FileUploadResult upload(UploadFileCommand command) {
        UploadContent content = command.content();
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        UploadLimits effective = effectiveLimits(command.limits());
        // 预检提前失败，避免无谓的 MD5 计算；上限的最终校验统一在 persistFile
        if (content.size() > effective.maxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        validateQuota(content.size(), command.uploaderId(), effective.quotaBytes());
        // 普通上传仅面向接口层已按上限预检的小文件，内容此时已全量在内存，直接计算摘要
        byte[] bytes = content.content();
        return persistFile(new ByteArrayInputStream(bytes), content.originalName(), content.contentType(),
                bytes.length, DigestUtils.md5DigestAsHex(bytes), command.bucket(), command.uploaderId(), effective);
    }

    @Override
    public FileRapidUploadResult rapidUpload(RapidUploadCommand command) {
        UploadLimits effective = effectiveLimits(command.limits());
        validateDeclaredSize(command.size(), effective);
        // MD5 大小写由仓储端口归一化，应用层按调用方原值透传
        String md5 = command.md5();
        FileAssetAggregate existing = fileAssetRepository.findReusableByDigest(md5, command.size()).orElse(null);
        if (existing == null) {
            return new FileRapidUploadResult(false, null);
        }
        validateQuota(command.size(), command.uploaderId(), effective.quotaBytes());
        FileAssetAggregate asset = FileAssetAggregate.reuse(existing, resolveOriginalName(command.fileName()),
                command.contentType(), command.uploaderId());
        return new FileRapidUploadResult(true, toUploadResult(persistAsset(asset, effective)));
    }

    @Override
    public FileQuotaResult quota(Long uploaderId, UploadLimits limits) {
        long quota = effectiveLimits(limits).quotaBytes();
        // 防御性兜底：Web 入口经 requireUserId 不会为 null，内网直调或无配额时按不限处理
        if (uploaderId == null || quota <= 0) {
            return new FileQuotaResult(0L, quota, 0L, true);
        }
        long used = fileAssetRepository.sumNormalSizeByUploader(uploaderId);
        return new FileQuotaResult(used, quota, Math.max(quota - used, 0L), false);
    }

    @Override
    public PageResult<FileInfoResult> page(FilePageQuery query) {
        long pageCurrent = clampPageCurrent(query.current());
        long size = clampPageSize(query.size());
        // 无管理权限时仅可见本人上传的文件；服务间上传（uploaderId 为空）仅管理视角可见
        Long uploaderFilter = query.canManage() ? null : query.requesterId();
        FileAssetQuery assetQuery = new FileAssetQuery(query.originalName(), query.bucket(),
                query.createTimeStart(), query.createTimeEnd(), uploaderFilter,
                pageCurrent, size);
        FileAssetPage page = fileAssetRepository.page(assetQuery);
        List<FileInfoResult> records = page.records().stream().map(FileInfoServiceImpl::toInfoResult).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    public FileInfoResult getDetail(Long id, Long requesterId, boolean canManage) {
        return toInfoResult(requireAccessibleFile(id, requesterId, canManage));
    }

    @Override
    public FileChangeResult getChangeDetail(Long id) {
        FileAssetAggregate asset = fileAssetRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        asset.assertVisibleToTenant(TenantContext.get());
        return toChangeResult(asset);
    }

    @Override
    public InputStream openContent(Long id, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        return fileStorage.load(asset.getBucket(), asset.getObjectKey());
    }

    @Override
    public InputStream openContent(Long id, Long requesterId, boolean canManage, long offset, long length) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        return fileStorage.load(asset.getBucket(), asset.getObjectKey(), offset, length);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        asset.assertDeletable();
        fileAssetRepository.deleteById(id);
    }

    @Override
    public PageResult<FileChangeResult> changes(FileChangesQuery query) {
        long pageCurrent = clampPageCurrent(query.current());
        long pageSize = clampPageSize(query.size());
        FileAssetPage page = fileAssetRepository.findChanges(query.since(), pageCurrent, pageSize);
        List<FileChangeResult> records = page.records().stream().map(FileInfoServiceImpl::toChangeResult).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id) {
        FileAssetAggregate asset = requireFile(id);
        if (!asset.lock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        FileAssetAggregate asset = requireFile(id);
        if (!asset.unlock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    public FilePresignedResult presignedUrl(Long id, Integer expiresSeconds, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        int expires = resolveExpiresSeconds(expiresSeconds);
        return new FilePresignedResult(
                fileStorage.presignedDownloadUrl(asset.getBucket(), asset.getObjectKey(), expires), expires);
    }

    @Override
    public ChunkUploadInitResult initChunkUpload(ChunkUploadInitCommand command) {
        long size = command.size();
        UploadLimits effective = effectiveLimits(command.limits());
        validateDeclaredSize(size, effective);
        ChunkUploadSessionId sessionId = StringUtils.hasText(command.uploadId())
                ? ChunkUploadSessionId.of(command.uploadId())
                : ChunkUploadSessionId.random();
        // 同一会话重复 init 时保留既有分片，实现断点续传；生效上限随会话登记，
        // 供 complete 阶段与当前上限取更严格者，防止两阶段绕过
        chunkUploadStore.initialize(sessionId,
                new ChunkUploadMetadata(command.fileName(), command.contentType(), size,
                        effective.maxSizeBytes(), effective.quotaBytes()));
        return new ChunkUploadInitResult(sessionId.value(), resolveChunkSize(),
                chunkUploadStore.uploadedPartIndexes(sessionId));
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
    public FileUploadResult completeChunkUpload(ChunkUploadCompleteCommand command) {
        long size = command.size();
        ChunkUploadSessionId sessionId = requireSession(command.uploadId());
        // complete 阶段同时校验「init 登记的生效上限」与「当前请求的上限」，任一阶段权限被回收都失败
        UploadLimits effective = UploadLimits.tighten(effectiveLimits(command.limits()),
                recordedLimits(chunkUploadStore.metadata(sessionId)));
        validateDeclaredSize(size, effective);
        // 配额失败保留分片供配额释放后续传；放弃或长期未完成的会话由暂存端口按 TTL 清理
        validateQuota(size, command.uploaderId(), effective.quotaBytes());
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
            FileUploadResult result = persistFile(merged.content(), command.fileName(), merged.contentType(),
                    size, merged.md5(), command.bucket(), command.uploaderId(), effective);
            // 仅在合并落存储成功后清理暂存数据；失败时保留分片供客户端续传/重试
            chunkUploadStore.discard(sessionId);
            return result;
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
     * @param md5 内容 MD5 摘要（十六进制小写）；<b>必须与 {@code in} 内容一致，由调用方保证</b>
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @param limits 生效的上传上限与配额
     * @return 上传结果
     * @throws BizException 大小超限时抛出 {@code PARAM_ERROR}
     */
    private FileUploadResult persistFile(InputStream in, String originalName, String contentType,
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
            FileAssetAggregate asset = FileAssetAggregate.register(resolvedBucket, objectKey, name, fileSize, contentType, md5, uploaderId);
            return toUploadResult(persistAsset(asset, limits));
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
    private FileAssetAggregate persistAsset(FileAssetAggregate asset, UploadLimits limits) {
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
    private FileAssetAggregate requireFile(Long id) {
        FileAssetAggregate asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        asset.assertVisibleToTenant(TenantContext.get());
        return asset;
    }

    /**
     * 查询文件资产并校验请求者可见性，不存在、跨租户或越权时统一抛出 {@code NOT_FOUND}
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID，可为 {@code null}（内网服务调用）
     * @param canManage 是否持有文件管理权限
     * @return 文件资产聚合根
     * @throws BizException 不可见时抛出 {@code NOT_FOUND}
     */
    private FileAssetAggregate requireAccessibleFile(Long id, Long requesterId, boolean canManage) {
        // 复用 requireFile 保证租户校验恒定发生，不受领域方法内部实现调整影响
        FileAssetAggregate asset = requireFile(id);
        asset.assertVisibleTo(requesterId, canManage, TenantContext.get());
        return asset;
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
     * 收敛页码：至少为 1，避免负偏移量传入仓储
     *
     * @param current 调用方请求的页码
     * @return 生效的页码
     */
    private static long clampPageCurrent(long current) {
        return Math.max(current, 1L);
    }

    /**
     * 收敛分页大小：至少 1 条，最多 {@link #MAX_PAGE_SIZE} 条，供所有分页入口复用
     *
     * @param size 调用方请求的每页条数
     * @return 生效的每页条数
     */
    private static long clampPageSize(long size) {
        return Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
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
     * <p>租户段取 {@link TenantContext#segment()}：无租户上下文时返回未知租户标识 {@code 0}，
     * 不会产生空段或双斜杠；此类对象键仅对内网无租户态调用可见。</p>
     *
     * @param originalName 原始文件名（仅用于提取扩展名，不参与对象键路径，客户端伪造文件名无法穿越路径）
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
     * 文件资产转上传结果
     *
     * @param asset 文件资产聚合根
     * @return 上传结果
     */
    private static FileUploadResult toUploadResult(FileAssetAggregate asset) {
        return new FileUploadResult(asset.getId(), asset.getFileName(), asset.getOriginalName(),
                asset.getBucket(), asset.getObjectKey(), asset.getFileSize(), asset.getContentType(),
                asset.getMd5());
    }

    /**
     * 文件资产转信息结果
     *
     * @param asset 文件资产聚合根
     * @return 文件信息结果
     */
    private static FileInfoResult toInfoResult(FileAssetAggregate asset) {
        return new FileInfoResult(asset.getId(), asset.getFileName(), asset.getOriginalName(),
                asset.getBucket(), asset.getObjectKey(), asset.getFileSize(), asset.getContentType(),
                asset.getMd5(), asset.getUploaderId(), asset.getCreateTime());
    }

    /**
     * 文件资产转变更流结果
     *
     * @param asset 文件资产聚合根
     * @return 文件变更结果
     */
    private static FileChangeResult toChangeResult(FileAssetAggregate asset) {
        return new FileChangeResult(asset.getId(), asset.getFileName(), asset.getOriginalName(),
                asset.getBucket(), asset.getObjectKey(), asset.getFileSize(), asset.getContentType(),
                asset.getMd5(), asset.getStatus().getCode(), asset.getUploaderId(),
                asset.isDeleted() ? 1 : 0, asset.getCreateTime(), asset.getUpdateTime());
    }
}
