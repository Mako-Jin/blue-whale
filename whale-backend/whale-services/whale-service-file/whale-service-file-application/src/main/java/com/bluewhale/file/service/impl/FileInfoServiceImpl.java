package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.domain.event.publisher.DomainEventPublisher;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.event.FileAssetDomainEvent;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.model.FileRecyclePage;
import com.bluewhale.file.domain.model.FileRecycleQuery;
import com.bluewhale.file.domain.model.vo.StorageQuota;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.content.FileSignatureInspector;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.metrics.FileMetrics;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadCompleteCommand;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.command.FileBatchDeleteCommand;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.query.FileRecyclePageQuery;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.model.result.FileBatchDeleteItemResult;
import com.bluewhale.file.model.result.FileBatchDeleteResult;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FilePresignedResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileRecycleResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.model.result.FileVersionResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.CdnUrlRewriter;
import com.bluewhale.file.service.support.FileContentPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /** 版本号并发冲突的最大尝试次数（含首次） */
    private static final int MAX_VERSION_CONFLICT_ATTEMPTS = 3;

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final ChunkUploadStore chunkUploadStore;

    private final FileProperties fileProperties;

    private final FileContentPolicy fileContentPolicy;

    private final StoragePluginCatalog storagePluginCatalog;

    private final FileMetrics fileMetrics;

    private final DomainEventPublisher domainEventPublisher;

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
        // 普通上传仅面向接口层已按上限预检的小文件，内容此时已全量在内存：
        // 先按魔数校验真实类型（失败不产生配额查询与摘要计算），再进入配额与落库流程
        byte[] bytes = content.content();
        fileContentPolicy.validate(content.originalName(), headerOf(bytes));
        validateQuota(content.size(), command.uploaderId(), resolveBucket(command.bucket()), effective.quotaBytes());
        long startedAt = System.nanoTime();
        try {
            FileUploadResult result = persistFile(new ByteArrayInputStream(bytes), content.originalName(),
                    content.contentType(), bytes.length, DigestUtils.md5DigestAsHex(bytes), command.bucket(),
                    command.uploaderId(), effective);
            fileMetrics.recordUpload("multipart", bytes.length, System.nanoTime() - startedAt, "success");
            return result;
        } catch (RuntimeException e) {
            fileMetrics.recordUpload("multipart", bytes.length, System.nanoTime() - startedAt, "failure");
            throw e;
        }
    }

    @Override
    public FileVersionResult uploadVersion(Long id, UploadContent content, Long requesterId, boolean canManage,
                                           UploadLimits limits) {
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        UploadLimits effective = effectiveLimits(limits);
        if (content.size() > effective.maxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        root.assertVersionUploadable();
        byte[] bytes = content.content();
        String name = resolveOriginalName(content.originalName());
        // 与普通上传一致：先按魔数校验真实类型，再进入配额与落库流程
        fileContentPolicy.validate(name, headerOf(bytes));
        validateQuota(bytes.length, requesterId, root.getBucket(), effective.quotaBytes());
        String objectKey = buildObjectKey(name);
        long startedAt = System.nanoTime();
        try {
            fileStorage.store(root.getBucket(), objectKey, new ByteArrayInputStream(bytes));
            FileAssetAggregate version;
            try {
                version = registerVersionWithRetry(root, objectKey, name, bytes.length, content.contentType(),
                        DigestUtils.md5DigestAsHex(bytes), requesterId, effective);
            } catch (RuntimeException e) {
                // 落库失败（含配额超限）：补偿删除已写入的物理对象，避免产生孤儿文件
                deleteStoredQuietly(root.getBucket(), objectKey);
                throw e;
            }
            FileVersionResult result = toVersionResult(version, true);
            fileMetrics.recordUpload("version", bytes.length, System.nanoTime() - startedAt, "success");
            pruneVersionsQuietly(root, requesterId);
            return result;
        } catch (RuntimeException e) {
            fileMetrics.recordUpload("version", bytes.length, System.nanoTime() - startedAt, "failure");
            throw e;
        }
    }

    @Override
    public List<FileVersionResult> versions(Long id, Long requesterId, boolean canManage) {
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        // 仓储按版本号倒序返回未删除版本，首条即当前最新版本
        List<FileAssetAggregate> found = fileAssetRepository.findVersions(root.getId());
        return IntStream.range(0, found.size())
                .mapToObj(index -> toVersionResult(found.get(index), index == 0))
                .toList();
    }

    @Override
    public void deleteVersion(Long id, int targetVersion, Long requesterId, boolean canManage) {
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        FileAssetAggregate target = fileAssetRepository.findVersions(root.getId()).stream()
                .filter(version -> version.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (target.getParentId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "根版本不可单独删除，请删除整个文件");
        }
        FileAssetAggregate latest = fileAssetRepository.findLatestVersion(root.getId()).orElse(null);
        if (latest != null && latest.getId().equals(target.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "最新版本不可删除，请先回滚或上传新版本");
        }
        if (fileAssetRepository.deleteVersionById(target.getId())) {
            publishFileEvent(target, FileAssetDomainEvent.ChangeType.DELETED, requesterId);
        }
    }

    @Override
    public List<FileVersionResult> deletedVersions(Long id, Long requesterId, boolean canManage) {
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        return fileAssetRepository.findDeletedVersions(root.getId()).stream()
                .filter(version -> version.getParentId() != null)
                .map(FileInfoServiceImpl::toVersionResult)
                .toList();
    }

    @Override
    public FileVersionResult restoreVersion(Long id, int targetVersion, Long requesterId, boolean canManage,
                                            UploadLimits limits) {
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        FileAssetAggregate target = fileAssetRepository.findDeletedVersions(root.getId()).stream()
                .filter(version -> version.getParentId() != null && version.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        try {
            fileAssetRepository.restoreWithQuotaCheck(target.getId(), quotaOf(effectiveLimits(limits).quotaBytes()));
            publishFileEvent(target, FileAssetDomainEvent.ChangeType.RESTORED, requesterId);
            fileMetrics.recordRestore(true);
        } catch (RuntimeException e) {
            fileMetrics.recordRestore(false);
            throw e;
        }
        // 恢复后重新读取版本组，首条为当前最新版本
        boolean latest = fileAssetRepository.findVersions(root.getId()).stream()
                .findFirst()
                .map(version -> version.getVersion() == target.getVersion())
                .orElse(false);
        return toVersionResult(target, latest);
    }

    @Override
    public FileInfoResult versionDetail(Long id, int targetVersion, Long requesterId, boolean canManage) {
        return toInfoResult(resolveVersion(id, targetVersion, requesterId, canManage));
    }

    @Override
    public InputStream openVersionContent(Long id, int targetVersion, Long requesterId, boolean canManage) {
        FileAssetAggregate version = resolveVersion(id, targetVersion, requesterId, canManage);
        long startedAt = System.nanoTime();
        InputStream content = fileStorage.load(version.getBucket(), version.getObjectKey(),
                version.getStorageType());
        fileMetrics.recordDownload(version.getFileSize(), System.nanoTime() - startedAt);
        return content;
    }

    @Override
    public InputStream openVersionContent(Long id, int targetVersion, Long requesterId, boolean canManage,
                                          long offset, long length) {
        FileAssetAggregate version = resolveVersion(id, targetVersion, requesterId, canManage);
        long startedAt = System.nanoTime();
        InputStream content = fileStorage.load(version.getBucket(), version.getObjectKey(), offset, length,
                version.getStorageType());
        fileMetrics.recordDownload(length, System.nanoTime() - startedAt);
        return content;
    }

    /**
     * 定位版本组内未删除的指定版本
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param targetVersion 目标版本号
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 版本资产
     * @throws BizException 版本不存在时抛出 {@code NOT_FOUND}
     */
    private FileAssetAggregate resolveVersion(Long id, int targetVersion, Long requesterId, boolean canManage) {
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        return fileAssetRepository.findVersions(root.getId()).stream()
                .filter(version -> version.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    @Override
    public int pruneExcessVersions() {
        int maxVersions = fileProperties.getVersion().getMaxVersions();
        if (maxVersions <= 0) {
            return 0;
        }
        int batchSize = fileProperties.getVersion().getCleanupBatchSize();
        long afterId = 0L;
        int pruned = 0;
        while (true) {
            List<Long> rootIds = fileAssetRepository.findVersionRootIdsWithExcess(
                    Math.max(maxVersions - 1, 0), afterId, batchSize);
            if (rootIds.isEmpty()) {
                break;
            }
            for (Long rootId : rootIds) {
                afterId = Math.max(afterId, rootId);
                FileAssetAggregate root = fileAssetRepository.findById(rootId).orElse(null);
                if (root == null) {
                    continue;
                }
                pruned += pruneVersions(root, null, false);
            }
            if (rootIds.size() < batchSize) {
                break;
            }
        }
        return pruned;
    }

    /**
     * 按版本保留上限清理最旧版本；清理失败不影响本次版本写入。
     *
     * @param root 版本组根文件
     * @param requesterId 调用者用户 ID
     */
    private void pruneVersionsQuietly(FileAssetAggregate root, Long requesterId) {
        try {
            pruneVersions(root, requesterId, true);
        } catch (RuntimeException e) {
            log.warn("历史版本清理失败，不影响本次版本写入: rootId={}", root.getId(), e);
        }
    }

    /**
     * 按版本保留上限清理最旧版本。
     *
     * <p>保留根版本与最近 {@code max-versions - 1} 个版本；清理顺序为「先删物理对象、再物理删除
     * 版本行、最后按需发布删除事件」。单个版本清理失败只告警，保留行待下次新版本产生时重试。</p>
     *
     * @param root 版本组根文件
     * @param requesterId 调用者用户 ID
     * @param publishEvents 是否发布删除事件；定时清理无租户上下文时传 {@code false}
     * @return 实际物理删除的版本行数
     */
    private int pruneVersions(FileAssetAggregate root, Long requesterId, boolean publishEvents) {
        int maxVersions = fileProperties.getVersion().getMaxVersions();
        if (maxVersions <= 0) {
            return 0;
        }
        List<FileAssetAggregate> versions = fileAssetRepository.findVersions(root.getId());
        if (versions.size() <= maxVersions) {
            return 0;
        }
        // 版本列表按版本号倒序且含根版本：根版本必须保留，因此只对非根版本保留最近 maxVersions - 1 个
        List<FileAssetAggregate> removable = versions.stream()
                .filter(version -> version.getParentId() != null)
                .skip(Math.max(maxVersions - 1L, 0L))
                .toList();
        int pruned = 0;
        for (FileAssetAggregate version : removable) {
            try {
                fileStorage.delete(version.getBucket(), version.getObjectKey(), version.getStorageType());
                if (fileAssetRepository.purgeVersionById(version.getId())) {
                    pruned++;
                    if (publishEvents) {
                        publishFileEvent(version, FileAssetDomainEvent.ChangeType.DELETED, requesterId);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("历史版本清理失败，保留待下次重试: id={}, version={}", version.getId(),
                        version.getVersion(), e);
            }
        }
        return pruned;
    }

    @Override
    public FileVersionResult rollback(Long id, int targetVersion, Long requesterId, boolean canManage,
                                      UploadLimits limits) {
        if (targetVersion < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        UploadLimits effective = effectiveLimits(limits);
        FileAssetAggregate root = resolveVersionRoot(id, requesterId, canManage);
        root.assertVersionUploadable();
        FileAssetAggregate source = fileAssetRepository.findVersions(root.getId()).stream()
                .filter(version -> version.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        // 复制会新增一份独立物理对象，按目标版本字节数计入配额
        validateQuota(source.getFileSize(), requesterId, root.getBucket(), effective.quotaBytes());
        String objectKey = buildObjectKey(source.getOriginalName());
        long startedAt = System.nanoTime();
        try {
            try (InputStream in = fileStorage.load(source.getBucket(), source.getObjectKey(),
                    source.getStorageType())) {
                fileStorage.store(root.getBucket(), objectKey, in);
            }
            FileAssetAggregate version = registerVersionWithRetry(root, objectKey, source.getOriginalName(),
                    source.getFileSize(), source.getContentType(), source.getMd5(), requesterId, effective);
            FileVersionResult result = toVersionResult(version, true);
            fileMetrics.recordUpload("rollback", source.getFileSize(), System.nanoTime() - startedAt, "success");
            pruneVersionsQuietly(root, requesterId);
            return result;
        } catch (IOException e) {
            // 读取或关闭源对象失败：清理可能已写入的新对象后按系统错误返回
            deleteStoredQuietly(root.getBucket(), objectKey);
            fileMetrics.recordUpload("rollback", source.getFileSize(), System.nanoTime() - startedAt, "failure");
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        } catch (RuntimeException e) {
            deleteStoredQuietly(root.getBucket(), objectKey);
            fileMetrics.recordUpload("rollback", source.getFileSize(), System.nanoTime() - startedAt, "failure");
            throw e;
        }
    }

    @Override
    public FileRapidUploadResult rapidUpload(RapidUploadCommand command) {
        UploadLimits effective = effectiveLimits(command.limits());
        validateDeclaredSize(command.size(), effective);
        // MD5 大小写由仓储端口归一化，应用层按调用方原值透传
        String md5 = command.md5();
        String originalName = resolveOriginalName(command.fileName());
        // 同用户、同文件名、同摘要与大小视为同一逻辑文件：直接返回既有记录，不落新行、不重复计配额
        FileAssetAggregate sameUser = fileAssetRepository
                .findReusableByUploaderAndDigest(command.uploaderId(), md5, command.size(), originalName)
                .orElse(null);
        if (sameUser != null) {
            fileMetrics.recordUpload("rapid", command.size(), 0L, "success");
            return new FileRapidUploadResult(true, toUploadResult(sameUser));
        }
        FileAssetAggregate existing = fileAssetRepository.findReusableByDigest(md5, command.size()).orElse(null);
        if (existing == null) {
            fileMetrics.recordUpload("rapid", command.size(), 0L, "miss");
            return new FileRapidUploadResult(false, null);
        }
        // 秒传复用已有物理对象，桶维度按源文件所在桶计量
        validateQuota(command.size(), command.uploaderId(), existing.getBucket(), effective.quotaBytes());
        FileAssetAggregate asset = FileAssetAggregate.reuse(existing, originalName,
                command.contentType(), command.uploaderId());
        long startedAt = System.nanoTime();
        try {
            FileUploadResult result = toUploadResult(persistAsset(asset, effective));
            fileMetrics.recordUpload("rapid", command.size(), System.nanoTime() - startedAt, "success");
            return new FileRapidUploadResult(true, result);
        } catch (RuntimeException e) {
            fileMetrics.recordUpload("rapid", command.size(), System.nanoTime() - startedAt, "failure");
            throw e;
        }
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
        // 无管理权限时仅可见本人上传的文件；服务间上传（uploaderId 为空）仅管理视角可见；
        // onlyMine=true（管理员「我的文件」视图）时无论是否持管理权限都只查本人文件
        Long uploaderFilter = query.canManage() && !query.onlyMine() ? null : query.requesterId();
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
        long startedAt = System.nanoTime();
        InputStream content = fileStorage.load(asset.getBucket(), asset.getObjectKey(), asset.getStorageType());
        fileMetrics.recordDownload(asset.getFileSize(), System.nanoTime() - startedAt);
        return content;
    }

    @Override
    public InputStream openContent(Long id, Long requesterId, boolean canManage, long offset, long length) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        long startedAt = System.nanoTime();
        InputStream content = fileStorage.load(asset.getBucket(), asset.getObjectKey(), offset, length,
                asset.getStorageType());
        fileMetrics.recordDownload(length, System.nanoTime() - startedAt);
        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long requesterId, boolean canManage) {
        deleteWithVersions(requireAccessibleFile(id, requesterId, canManage), requesterId);
    }

    @Override
    public FileBatchDeleteResult batchDelete(FileBatchDeleteCommand command) {
        List<Long> ids = distinctIds(command.ids());
        int maxFiles = fileProperties.getBatch().getMaxFiles();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件ID列表不能为空");
        }
        if (ids.size() > maxFiles) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "单次最多批量删除 " + maxFiles + " 个文件");
        }
        List<FileBatchDeleteItemResult> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            items.add(deleteOne(id, command.requesterId(), command.canManage()));
        }
        return new FileBatchDeleteResult(items);
    }

    @Override
    public PageResult<FileRecycleResult> recyclePage(FileRecyclePageQuery query) {
        long pageCurrent = clampPageCurrent(query.current());
        long pageSize = clampPageSize(query.size());
        // 无管理权限时仅可见本人上传的已删文件；管理视角可查看全租户回收站
        Long uploaderFilter = query.canManage() ? null : query.requesterId();
        FileRecyclePage page = fileAssetRepository.pageRecycle(new FileRecycleQuery(query.originalName(),
                query.bucket(), uploaderFilter, pageCurrent, pageSize));
        List<FileRecycleResult> records = page.records().stream()
                .map(FileInfoServiceImpl::toRecycleResult)
                .toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, Long requesterId, boolean canManage, UploadLimits limits) {
        FileAssetAggregate target = fileAssetRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        FileAssetAggregate root = target.getParentId() == null ? target
                : fileAssetRepository.findByIdIncludingDeleted(target.getParentId())
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        // 可见性以版本组根文件为准，避免通过本人上传的版本行绕过根文件归属校验
        root.assertVisibleTo(requesterId, canManage, TenantContext.get());
        boolean restoreGroup = root.isDeleted();
        if (!restoreGroup && !target.isDeleted()) {
            // 幂等：已恢复的文件重复调用按成功处理
            fileMetrics.recordRestore(true);
            return;
        }
        // 组内已删除版本需在恢复前读取：恢复后这些行不再满足 deleted = 1
        List<FileAssetAggregate> restoredVersions = restoreGroup
                ? fileAssetRepository.findDeletedVersions(root.getId())
                : List.of();
        try {
            fileAssetRepository.restoreWithQuotaCheck(restoreGroup ? root.getId() : target.getId(),
                    quotaOf(effectiveLimits(limits).quotaBytes()));
            if (restoreGroup) {
                publishFileEvent(root, FileAssetDomainEvent.ChangeType.RESTORED, requesterId);
                restoredVersions.forEach(version ->
                        publishFileEvent(version, FileAssetDomainEvent.ChangeType.RESTORED, requesterId));
            } else {
                publishFileEvent(target, FileAssetDomainEvent.ChangeType.RESTORED, requesterId);
            }
            fileMetrics.recordRestore(true);
        } catch (RuntimeException e) {
            fileMetrics.recordRestore(false);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String originalName, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        if (!asset.rename(originalName)) {
            return;
        }
        fileAssetRepository.updateOriginalName(id, asset.getOriginalName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long id, String targetBucket, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        String sourceBucket = asset.getBucket();
        if (!asset.moveTo(targetBucket)) {
            return;
        }
        // 先迁物理对象：失败时元数据不变，文件仍可下载
        fileStorage.move(sourceBucket, asset.getObjectKey(), asset.getBucket(), asset.getStorageType());
        try {
            fileAssetRepository.updateBucket(id, asset.getBucket());
        } catch (RuntimeException e) {
            // 元数据更新失败：尽力回迁，避免元数据与对象长时间错位；回迁失败仅告警由人工介入
            try {
                fileStorage.move(asset.getBucket(), asset.getObjectKey(), sourceBucket, asset.getStorageType());
            } catch (RuntimeException rollbackError) {
                log.error("文件移动元数据失败且物理回迁失败，需人工核对: id={}, bucket={}->{}",
                        id, sourceBucket, asset.getBucket(), rollbackError);
            }
            throw e;
        }
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
        publishFileEvent(asset, FileAssetDomainEvent.ChangeType.LOCKED, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        FileAssetAggregate asset = requireFile(id);
        if (!asset.unlock()) {
            return;
        }
        fileAssetRepository.save(asset);
        publishFileEvent(asset, FileAssetDomainEvent.ChangeType.UNLOCKED, null);
    }

    @Override
    public FilePresignedResult presignedUrl(Long id, Integer expiresSeconds, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireAccessibleFile(id, requesterId, canManage);
        if (fileProperties.getStorage().getEncryption().isEnabled()) {
            // 预签名绕过应用层，加密对象会返回密文；引导调用方走网关下载以透明解密
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "已启用落盘加密，预签名地址不可用，请使用下载接口");
        }
        int expires = resolveExpiresSeconds(expiresSeconds);
        String url = fileStorage.presignedDownloadUrl(asset.getBucket(), asset.getObjectKey(), expires,
                asset.getStorageType());
        // 配置了 CDN 时仅替换主机部分，路径与签名查询串保持不变；未配置时原样返回
        return new FilePresignedResult(CdnUrlRewriter.rewrite(url, fileProperties.getCdn().getBaseUrl()), expires);
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
        validateQuota(size, command.uploaderId(), resolveBucket(command.bucket()), effective.quotaBytes());
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
        FileUploadResult result;
        long startedAt = System.nanoTime();
        try (MergedChunkUpload merged = chunkUploadStore.merge(sessionId, expectedCount)) {
            if (merged.size() != size) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片总大小与声明大小不一致");
            }
            result = persistFile(sniffContent(merged.content(), command.fileName()), command.fileName(),
                    merged.contentType(), size, merged.md5(), command.bucket(), command.uploaderId(), effective);
            fileMetrics.recordUpload("chunk", size, System.nanoTime() - startedAt, "success");
        } catch (IOException e) {
            fileMetrics.recordUpload("chunk", size, System.nanoTime() - startedAt, "failure");
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        } catch (RuntimeException e) {
            fileMetrics.recordUpload("chunk", size, System.nanoTime() - startedAt, "failure");
            throw e;
        }
        // 仅在合并落存储成功后清理暂存数据；失败时保留分片供客户端续传/重试。
        // 必须在合并流关闭后执行：Windows 下删除被占用文件会失败，导致暂存目录残留
        chunkUploadStore.discard(sessionId);
        return result;
    }

    /**
     * 读取合并流头部做魔数校验并回推，保证后续写入存储的仍是完整内容。
     *
     * <p>使用 {@link PushbackInputStream} 而非缓存整份内容：分片合并结果可能为大文件，
     * 只回推嗅探所需的头部字节。校验失败时由外层 try-with-resources 关闭合并流，
     * 分片暂存保留供修复后重试。</p>
     *
     * @param content 合并后的内容流，由调用方负责关闭
     * @param fileName 原始文件名（用于扩展名比对）
     * @return 已回推头部的内容流
     * @throws IOException 读取头部失败时抛出
     */
    private InputStream sniffContent(InputStream content, String fileName) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(content, FileSignatureInspector.HEADER_LENGTH);
        byte[] header = pushback.readNBytes(FileSignatureInspector.HEADER_LENGTH);
        fileContentPolicy.validate(fileName, header);
        pushback.unread(header);
        return pushback;
    }

    /**
     * 取内容前若干字节作为魔数校验头部
     *
     * @param content 完整内容
     * @return 头部字节，长度不超过 {@link FileSignatureInspector#HEADER_LENGTH}
     */
    private static byte[] headerOf(byte[] content) {
        return content.length <= FileSignatureInspector.HEADER_LENGTH
                ? content : Arrays.copyOf(content, FileSignatureInspector.HEADER_LENGTH);
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
        String resolvedBucket = resolveBucket(bucket);
        String objectKey = buildObjectKey(name);
        fileStorage.store(resolvedBucket, objectKey, in);
        try {
            FileAssetAggregate asset = FileAssetAggregate.register(resolvedBucket, objectKey, name, fileSize,
                    contentType, md5, uploaderId, storagePluginCatalog.activeType());
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
        return fileAssetRepository.saveWithQuotaCheck(asset, quotaOf(limits.quotaBytes()));
    }

    /**
     * 登记版本组新版本行；版本号并发冲突时重算并重试。
     *
     * <p>版本号由「组内最大版本号 + 1」计算，并发上传/回滚可能算出相同版本号；唯一索引
     * {@code uk_parent_version(parent_id, version)} 拒绝后重读最新版本号重试，避免整次操作失败。
     * 物理对象在进入本方法前已落盘，重试不重复写存储；重试耗尽时抛出原始冲突异常，
     * 由调用方补偿删除物理对象。</p>
     *
     * @param root 版本组根文件
     * @param objectKey 已落盘的对象键
     * @param originalName 原始文件名
     * @param fileSize 文件字节数
     * @param contentType 内容类型
     * @param md5 内容摘要
     * @param requesterId 上传者用户 ID
     * @param limits 生效的上传上限与配额
     * @return 落库后的版本资产
     * @throws DuplicateKeyException 重试耗尽仍冲突时抛出
     */
    private FileAssetAggregate registerVersionWithRetry(FileAssetAggregate root, String objectKey,
                                                        String originalName, long fileSize, String contentType,
                                                        String md5, Long requesterId, UploadLimits limits) {
        for (int attempt = 1; ; attempt++) {
            // 版本号取组内最大值 + 1；查询含已删除行，保证删除版本后序号不复用
            int nextVersion = fileAssetRepository.findLatestVersion(root.getId())
                    .map(latest -> latest.getVersion() + 1)
                    .orElse(root.getVersion() + 1);
            FileAssetAggregate version = FileAssetAggregate.registerVersion(root, nextVersion, objectKey,
                    originalName, fileSize, contentType, md5, requesterId, storagePluginCatalog.activeType());
            try {
                return persistAsset(version, limits);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_VERSION_CONFLICT_ATTEMPTS) {
                    log.warn("版本号并发冲突重试耗尽: rootId={}, attempts={}", root.getId(), attempt);
                    throw e;
                }
                log.info("版本号并发冲突，重算版本号后重试: rootId={}, attempt={}", root.getId(), attempt);
            }
        }
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
     * 解析版本组根文件并校验可见性：传入版本行时回溯到 {@code parentId} 指向的根文件
     *
     * <p>可见性以根文件为准，避免通过「本人上传的版本行」绕过根文件归属校验。</p>
     *
     * @param id 文件ID（根文件或任一版本行）
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 版本组根文件
     * @throws BizException 根文件不存在、跨租户或越权时抛出 {@code NOT_FOUND}
     */
    private FileAssetAggregate resolveVersionRoot(Long id, Long requesterId, boolean canManage) {
        FileAssetAggregate asset = requireFile(id);
        FileAssetAggregate root = asset.getParentId() == null ? asset : requireFile(asset.getParentId());
        root.assertVisibleTo(requesterId, canManage, TenantContext.get());
        return root;
    }

    /**
     * 批量删除单项：异常收敛为单项失败原因，不中断整批
     *
     * <p>本方法不声明事务，条件更新随调用即时提交；越权、不存在与归档锁定均只影响当前项。
     * 异常消息取业务异常自带文案，保证前端能区分「不存在」与「已锁定」。</p>
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 单项删除结果
     */
    private FileBatchDeleteItemResult deleteOne(Long id, Long requesterId, boolean canManage) {
        try {
            deleteWithVersions(requireAccessibleFile(id, requesterId, canManage), requesterId);
            return new FileBatchDeleteItemResult(id, true, null);
        } catch (BizException e) {
            return new FileBatchDeleteItemResult(id, false, e.getMessage());
        }
    }

    /**
     * 逻辑删除文件并级联处理版本组
     *
     * <p>目标为根文件时级联删除组内全部版本，且组内任一归档锁定文件都会导致整体拒绝；
     * 目标为版本行时仅删除该版本。删除后按文件逐个发布 {@code DELETED} 事件，
     * 保证下游（知识库）能移除每个版本对应的索引。</p>
     *
     * @param asset 已通过可见性校验的文件资产
     * @param requesterId 调用者用户 ID
     * @throws BizException 目标或组内版本归档锁定时抛出 {@code PARAM_ERROR}
     */
    private void deleteWithVersions(FileAssetAggregate asset, Long requesterId) {
        asset.assertDeletable();
        List<FileAssetAggregate> cascaded = asset.getParentId() == null
                ? fileAssetRepository.findVersions(asset.getId()).stream()
                        .filter(version -> !asset.getId().equals(version.getId()))
                        .toList()
                : List.of();
        cascaded.forEach(FileAssetAggregate::assertDeletable);
        fileAssetRepository.deleteById(asset.getId());
        publishFileEvent(asset, FileAssetDomainEvent.ChangeType.DELETED, requesterId);
        cascaded.forEach(version ->
                publishFileEvent(version, FileAssetDomainEvent.ChangeType.DELETED, requesterId));
    }

    /**
     * 去重并校验批量操作的文件ID：保留请求顺序，拒绝空 ID
     *
     * @param ids 请求的文件ID列表，可为 {@code null}
     * @return 去重后的文件ID列表
     * @throws BizException 列表为空或含空 ID 时抛出 {@code PARAM_ERROR}
     */
    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids.size());
        for (Long id : ids) {
            if (id == null) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件ID不能为空");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
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
     * 发布文件生命周期事件：载荷取当前聚合快照与租户上下文，同一业务操作只发一条。
     *
     * <p>事件为进程内通知，发布发生在事务提交前；消费方需要已提交状态时应使用
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}。发布失败不影响业务结果，
     * 由基础设施适配器统一降级为告警。</p>
     *
     * @param asset 文件资产（调用方须确保已存在主键）
     * @param changeType 变更类型
     * @param operatorId 操作人；内部锁定/解锁调用为 {@code null}
     */
    private void publishFileEvent(FileAssetAggregate asset, FileAssetDomainEvent.ChangeType changeType,
                                  Long operatorId) {
        domainEventPublisher.publish(new FileAssetDomainEvent(asset.getId(), changeType, TenantContext.get(),
                asset.getBucket(), asset.getObjectKey(), asset.getUploaderId(), operatorId));
    }

    /**
     * 乐观预检存储配额：个人、租户、桶三个维度依次校验，未配置上限或缺少对应上下文时跳过该维度。
     *
     * <p>并发安全由仓储端口 {@code saveWithQuotaCheck} 的临界区保证，本方法仅用于快速失败、
     * 避免无谓的存储写入。租户/桶维度只在存在租户上下文时校验：无租户态时租户拦截器不追加条件，
     * 聚合统计会跨租户，按全局用量拒绝会误伤内网调用。</p>
     *
     * @param incomingSize 本次拟写入的字节数
     * @param uploaderId 上传用户 ID；无登录态时为 {@code null}
     * @param bucket 生效的目标桶名称
     * @param userQuota 生效的个人配额；{@code <=0} 表示不限
     * @throws BizException 超出任一维度配额时抛出 {@code PARAM_ERROR}
     */
    private void validateQuota(long incomingSize, Long uploaderId, String bucket, long userQuota) {
        if (userQuota > 0 && uploaderId != null) {
            guardQuota(fileAssetRepository.sumNormalSizeByUploader(uploaderId), incomingSize, userQuota,
                    "超出个人存储配额");
        }
        if (TenantContext.get() == null) {
            return;
        }
        long tenantQuota = fileProperties.getQuota().getTenantBytes();
        if (tenantQuota > 0) {
            guardQuota(fileAssetRepository.sumNormalSizeByTenant(), incomingSize, tenantQuota, "超出租户存储配额");
        }
        long bucketQuota = fileProperties.getQuota().getBucketBytes();
        if (bucketQuota > 0) {
            guardQuota(fileAssetRepository.sumNormalSizeByBucket(bucket), incomingSize, bucketQuota,
                    "超出桶存储配额: " + bucket);
        }
    }

    /**
     * 校验单个维度的配额
     *
     * @param usedBytes 该维度已用字节数
     * @param incomingSize 本次拟写入的字节数
     * @param limitBytes 该维度上限
     * @param message 超限提示文案
     * @throws BizException 超出上限时抛出 {@code PARAM_ERROR}
     */
    private static void guardQuota(long usedBytes, long incomingSize, long limitBytes, String message) {
        if (usedBytes + incomingSize > limitBytes) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
        }
    }

    /**
     * 组装三维配额：个人维度取本次请求的生效配额，租户与桶维度取服务配置。
     *
     * @param userQuotaBytes 生效的个人配额；{@code <=0} 表示不限
     * @return 配额值对象
     */
    private StorageQuota quotaOf(long userQuotaBytes) {
        return new StorageQuota(userQuotaBytes, fileProperties.getQuota().getTenantBytes(),
                fileProperties.getQuota().getBucketBytes());
    }

    /**
     * 解析生效的桶名称：请求未指定时回落配置的默认桶
     *
     * @param bucket 请求指定的桶名称，可为 {@code null}；空白视为未指定
     * @return 生效的桶名称
     */
    private String resolveBucket(String bucket) {
        return StringUtils.hasText(bucket) ? bucket : fileProperties.getBucket().getDefault();
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
     * 文件资产转版本结果
     *
     * @param asset 文件资产聚合根
     * @return 文件版本结果
     */
    private static FileVersionResult toVersionResult(FileAssetAggregate asset) {
        return toVersionResult(asset, false);
    }

    /**
     * 文件资产转版本结果并标记是否最新版本
     *
     * @param asset 文件资产聚合根
     * @param latest 是否为版本组内当前最新版本
     * @return 文件版本结果
     */
    private static FileVersionResult toVersionResult(FileAssetAggregate asset, boolean latest) {
        return new FileVersionResult(asset.getId(), asset.getParentId(), asset.getVersion(), latest,
                asset.getFileName(), asset.getOriginalName(), asset.getBucket(), asset.getObjectKey(),
                asset.getFileSize(), asset.getContentType(), asset.getMd5(), asset.getUploaderId(),
                asset.getCreateTime());
    }

    /**
     * 回收站投影转结果
     *
     * @param item 回收站投影
     * @return 回收站结果
     */
    private static FileRecycleResult toRecycleResult(FileRecycleItem item) {
        return new FileRecycleResult(item.id(), item.fileName(), item.originalName(), item.bucket(),
                item.fileSize(), item.contentType(), item.status(), item.uploaderId(),
                item.createTime(), item.updateTime(), item.deleteTime());
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
