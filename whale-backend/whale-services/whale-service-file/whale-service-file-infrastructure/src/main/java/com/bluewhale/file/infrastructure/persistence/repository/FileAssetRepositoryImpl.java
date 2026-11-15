package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.model.FileAssetPage;
import com.bluewhale.file.domain.model.FileAssetQuery;
import com.bluewhale.file.domain.model.FileObjectRef;
import com.bluewhale.file.domain.model.FileRecycleItem;
import com.bluewhale.file.domain.model.FileRecyclePage;
import com.bluewhale.file.domain.model.FileRecycleQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.model.vo.StorageQuota;
import com.bluewhale.file.po.FileInfoPo;
import com.bluewhale.file.infrastructure.persistence.assembler.FileInfoPersistenceAssembler;
import com.bluewhale.file.mapper.FileInfoMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link FileAssetRepository} 的 MyBatis-Plus 实现：以 {@link FileInfoMapper} 完成 PO 读写，
 * 并借 {@link FileInfoPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * @author BlueWhale
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileAssetRepositoryImpl implements FileAssetRepository {

    /** 秒传去重查询的取条数上限：仅需第一条可复用记录；必须是编译期常量，禁止拼接外部输入 */
    private static final String LIMIT_ONE = "LIMIT 1";

    /** 配额命名锁前缀：按上传用户维度串行化「统计已用量 + 写入」 */
    private static final String QUOTA_LOCK_PREFIX = "whale:file:quota:";

    /** 租户配额命名锁前缀：按租户维度串行化 */
    private static final String TENANT_QUOTA_LOCK_PREFIX = "whale:file:quota:tenant:";

    /** 桶配额命名锁前缀：按租户内的桶维度串行化 */
    private static final String BUCKET_QUOTA_LOCK_PREFIX = "whale:file:quota:bucket:";

    /** 单次写入最多获取的配额锁数量（个人 / 租户 / 桶） */
    private static final int MAX_QUOTA_LOCK_SCOPES = 3;

    /** 配额命名锁等待上限（秒）：超时快速失败，避免请求长时间挂起 */
    private static final int QUOTA_LOCK_TIMEOUT_SECONDS = 5;

    /** 配额命名锁获取耗时指标名 */
    private static final String QUOTA_LOCK_TIMER = "file.quota.lock";

    private final FileInfoMapper fileInfoMapper;

    private final MeterRegistry meterRegistry;

    @Override
    public FileAssetAggregate save(FileAssetAggregate asset) {
        FileInfoPo po = FileInfoPersistenceAssembler.toPo(asset);
        if (po.getId() == null) {
            fileInfoMapper.insert(po);
        } else {
            fileInfoMapper.updateById(po);
        }
        return FileInfoPersistenceAssembler.toDomain(po);
    }

    /**
     * 在配额临界区内保存文件元数据。
     *
     * <p>配额统计与写入用 MySQL 命名锁串行化，锁随事务完成（提交/回滚）后释放；本方法使用独立
     * 事务（{@code REQUIRES_NEW}）让配额锁只覆盖临界区本身。调用方不得再包裹外层事务：本方法提交后
     * 的写入不随外层回滚，会破坏「存储写入 + 元数据登记」的补偿一致性。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public FileAssetAggregate saveWithQuotaCheck(FileAssetAggregate asset, StorageQuota quota) {
        if (quota == null || !quota.hasAnyLimit()) {
            return save(asset);
        }
        return withQuotaGuard(asset.getUploaderId(), asset.getBucket(), asset.getFileSize(), quota, "save",
                () -> save(asset));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean restoreWithQuotaCheck(Long id, StorageQuota quota) {
        FileInfoPo po = fileInfoMapper.selectByIdIncludingDeleted(id);
        if (po == null || po.getDeleted() == null || po.getDeleted() == 0) {
            // 已恢复或目标不存在：幂等返回 false，由应用层按成功处理
            return false;
        }
        if (quota == null || !quota.hasAnyLimit()) {
            return restore(id);
        }
        // 恢复量按版本组内「待恢复」字节合计：根文件恢复会连带还原组内已删除版本
        long size = fileInfoMapper.sumDeletedSizeOfGroup(id);
        return withQuotaGuard(po.getUploaderId(), po.getBucket(), size, quota, "restore", () -> restore(id));
    }

    /**
     * 在所需维度的命名锁内校验配额后执行动作。
     *
     * <p>只有「配置了正数上限」且「上下文具备该维度」时才取锁：个人维度要求上传者非空；
     * 租户与桶维度要求存在租户上下文——无租户态时租户拦截器不追加条件，聚合统计会跨租户，
     * 因此这两维直接跳过，内网无租户态调用不受限制。</p>
     *
     * @param uploaderId 上传者 ID，可为 {@code null}
     * @param bucket 目标桶名称
     * @param incomingSize 本次写入的字节数
     * @param quota 生效的配额上限
     * @param operation 操作标识（指标标签）
     * @param action 通过配额校验后执行的动作
     * @param <T> 返回类型
     * @return 动作执行结果
     */
    private <T> T withQuotaGuard(Long uploaderId, String bucket, long incomingSize, StorageQuota quota,
                                 String operation, Supplier<T> action) {
        List<String> lockNames = quotaLockNames(uploaderId, bucket, quota);
        if (lockNames.isEmpty()) {
            return action.get();
        }
        return withNestedQuotaLocks(lockNames, 0, operation, () -> {
            assertWithinQuota(incomingSize, uploaderId, bucket, quota);
            return action.get();
        });
    }

    /**
     * 按固定顺序逐层获取配额命名锁，避免多维度并发时形成锁等待环。
     *
     * @param lockNames 按获取顺序排列的锁名称
     * @param index 当前层级
     * @param operation 操作标识（指标标签）
     * @param action 全部锁获取后执行的动作
     * @param <T> 返回类型
     * @return 动作执行结果
     */
    private <T> T withNestedQuotaLocks(List<String> lockNames, int index, String operation, Supplier<T> action) {
        if (index >= lockNames.size()) {
            return action.get();
        }
        return withQuotaLock(lockNames.get(index), operation,
                () -> withNestedQuotaLocks(lockNames, index + 1, operation, action));
    }

    /**
     * 计算本次写入需要获取的配额锁，顺序固定为「个人 → 租户 → 桶」。
     *
     * @param uploaderId 上传者 ID，可为 {@code null}
     * @param bucket 目标桶名称
     * @param quota 生效的配额上限
     * @return 锁名称列表；无需限制时返回空列表
     */
    private static List<String> quotaLockNames(Long uploaderId, String bucket, StorageQuota quota) {
        List<String> names = new ArrayList<>(MAX_QUOTA_LOCK_SCOPES);
        if (quota.userLimited() && uploaderId != null) {
            names.add(QUOTA_LOCK_PREFIX + uploaderId);
        }
        if (TenantContext.get() == null) {
            return names;
        }
        if (quota.tenantLimited()) {
            names.add(TENANT_QUOTA_LOCK_PREFIX + TenantContext.get());
        }
        if (quota.bucketLimited()) {
            names.add(BUCKET_QUOTA_LOCK_PREFIX + bucket);
        }
        return names;
    }

    /**
     * 依次校验三个维度：已用量 + 本次写入量不得超过上限。
     *
     * @param incomingSize 本次写入的字节数
     * @param uploaderId 上传者 ID，可为 {@code null}
     * @param bucket 目标桶名称
     * @param quota 生效的配额上限
     * @throws BizException 任一维度超限时抛出 {@code PARAM_ERROR}
     */
    private void assertWithinQuota(long incomingSize, Long uploaderId, String bucket, StorageQuota quota) {
        if (quota.userLimited() && uploaderId != null) {
            guardQuota(sumNormalSizeByUploader(uploaderId), incomingSize, quota.userBytes(), "超出个人存储配额");
        }
        if (TenantContext.get() == null) {
            return;
        }
        if (quota.tenantLimited()) {
            guardQuota(sumNormalSizeByTenant(), incomingSize, quota.tenantBytes(), "超出租户存储配额");
        }
        if (quota.bucketLimited()) {
            guardQuota(sumNormalSizeByBucket(bucket), incomingSize, quota.bucketBytes(),
                    "超出桶存储配额: " + bucket);
        }
    }

    /**
     * 校验单个维度的配额
     *
     * @param usedBytes 已用字节数
     * @param incomingSize 本次写入字节数
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
     * 在 MySQL 命名锁临界区内执行配额相关操作，并记录锁获取耗时。
     *
     * <p>命名锁与会话（连接）绑定，必须等事务提交/回滚后再释放；非事务场景直接释放。
     * 事务同步注册失败（如事务已标记回滚）时 afterCompletion 不会触发，由 finally 兜底释放。</p>
     *
     * @param lockName 锁名称
     * @param operation 操作标识（指标标签）
     * @param action 临界区动作
     * @param <T> 返回类型
     * @return 临界区执行结果
     */
    private <T> T withQuotaLock(String lockName, String operation, Supplier<T> action) {
        long startedAt = System.nanoTime();
        Integer acquired = fileInfoMapper.acquireNamedLock(lockName, QUOTA_LOCK_TIMEOUT_SECONDS);
        meterRegistry.timer(QUOTA_LOCK_TIMER, "op", operation)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        if (acquired == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "获取文件配额锁失败，请稍后重试");
        }
        if (acquired != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "获取文件配额锁超时，请稍后重试");
        }
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
        boolean releaseAfterCompletion = false;
        try {
            if (synchronizationActive) {
                // 命名锁绑定当前连接：必须等事务提交/回滚后再释放，避免并发事务在提交前读到旧用量
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseQuotaLockQuietly(lockName);
                    }
                });
                releaseAfterCompletion = true;
            }
            return action.get();
        } finally {
            if (!releaseAfterCompletion) {
                releaseQuotaLockQuietly(lockName);
            }
        }
    }

    /**
     * 恢复逻辑删除行及其版本组
     *
     * @param id 文件ID（根文件或任一版本行）
     * @return 是否实际恢复根行
     */
    private boolean restore(Long id) {
        boolean restored = fileInfoMapper.restoreById(id) > 0;
        if (restored) {
            fileInfoMapper.restoreVersionsByRootId(id);
        }
        return restored;
    }

    @Override
    public Optional<FileAssetAggregate> findById(Long id) {
        FileInfoPo po = fileInfoMapper.selectById(id);
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<FileAssetAggregate> findByIdIncludingDeleted(Long id) {
        FileInfoPo po = fileInfoMapper.selectByIdIncludingDeleted(id);
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<FileAssetAggregate> findLatestVersion(Long rootId) {
        return Optional.ofNullable(fileInfoMapper.selectLatestVersion(rootId))
                .map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public List<FileAssetAggregate> findVersions(Long rootId) {
        return fileInfoMapper.selectVersions(rootId).stream()
                .map(FileInfoPersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public List<FileAssetAggregate> findDeletedVersions(Long rootId) {
        return fileInfoMapper.selectDeletedVersions(rootId).stream()
                .map(FileInfoPersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public Optional<FileAssetAggregate> findReusableByDigest(String md5, long fileSize) {
        if (!StringUtils.hasText(md5)) {
            return Optional.empty();
        }
        // 端口契约大小写不敏感：查询前统一归一化为小写，避免历史/外部写入的大写摘要查不到
        String normalized = md5.trim().toLowerCase(Locale.ROOT);
        FileInfoPo po = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfoPo>()
                .eq(FileInfoPo::getMd5, normalized)
                .eq(FileInfoPo::getFileSize, fileSize)
                .eq(FileInfoPo::getStatus, FileStatus.NORMAL.getCode())
                .orderByAsc(FileInfoPo::getId)
                .last(LIMIT_ONE));
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        fileInfoMapper.markDeletedById(id);
        // 级联删除版本行：版本行没有子行，因此同一方法同时覆盖根文件与版本行
        fileInfoMapper.markDeletedByRootId(id);
    }

    @Override
    public void updateOriginalName(Long id, String originalName) {
        fileInfoMapper.updateOriginalName(id, originalName);
    }

    @Override
    public void updateBucket(Long id, String bucket) {
        fileInfoMapper.updateBucket(id, bucket);
    }

    @Override
    public long sumNormalSizeByUploader(Long uploaderId) {
        QueryWrapper<FileInfoPo> wrapper = normalSizeWrapper();
        wrapper.eq("uploader_id", uploaderId);
        return sumNormalSize(wrapper);
    }

    @Override
    public long sumNormalSizeByTenant() {
        return sumNormalSize(normalSizeWrapper());
    }

    @Override
    public long sumNormalSizeByBucket(String bucket) {
        QueryWrapper<FileInfoPo> wrapper = normalSizeWrapper();
        wrapper.eq("bucket", bucket);
        return sumNormalSize(wrapper);
    }

    /**
     * 构造「正常文件字节数合计」查询骨架，各维度按需追加条件。
     *
     * <p>租户条件不在此显式追加，由 {@code TenantLineInnerInterceptor} 统一注入。</p>
     *
     * @return 查询条件
     */
    private static QueryWrapper<FileInfoPo> normalSizeWrapper() {
        QueryWrapper<FileInfoPo> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(file_size), 0) AS total")
                .eq("status", FileStatus.NORMAL.getCode());
        return wrapper;
    }

    /**
     * 执行聚合查询并把结果规整为字节数
     *
     * @param wrapper 聚合查询条件（必须选择单列合计值）
     * @return 合计字节数；无记录或空值时返回 0
     * @throws BizException 查询失败或结果类型异常时抛出 {@code SYSTEM_ERROR}
     */
    private long sumNormalSize(QueryWrapper<FileInfoPo> wrapper) {
        List<Object> values;
        try {
            // selectObjs 返回所查询列的值列表（每行一个元素）；无 GROUP BY 的聚合查询恒返回一行
            values = fileInfoMapper.selectObjs(wrapper);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0L;
        }
        return toBytes(values.get(0));
    }

    @Override
    public FileAssetPage page(FileAssetQuery query) {
        LambdaQueryWrapper<FileInfoPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.originalName()),
                FileInfoPo::getOriginalName, query.originalName());
        wrapper.eq(StringUtils.hasText(query.bucket()), FileInfoPo::getBucket, query.bucket());
        wrapper.ge(query.createTimeStart() != null, FileInfoPo::getCreateTime, query.createTimeStart());
        wrapper.le(query.createTimeEnd() != null, FileInfoPo::getCreateTime, query.createTimeEnd());
        wrapper.eq(query.uploaderId() != null, FileInfoPo::getUploaderId, query.uploaderId());
        // 文件列表只展示版本组根文件：版本行通过版本列表接口查询，避免重复条目污染分页
        wrapper.isNull(FileInfoPo::getParentId);
        // 追加主键二级排序：create_time 相同的行（如批量导入）在分页边界需要稳定顺序，否则可能重复或遗漏
        wrapper.orderByDesc(FileInfoPo::getCreateTime);
        wrapper.orderByDesc(FileInfoPo::getId);
        Page<FileInfoPo> page = fileInfoMapper.selectPage(new Page<>(query.current(), query.size()), wrapper);
        List<FileAssetAggregate> records = page.getRecords().stream().map(FileInfoPersistenceAssembler::toDomain).toList();
        return new FileAssetPage(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileAssetPage findChanges(LocalDateTime since, long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        // 游标（since）先行过滤大部分数据，offset 只作用于 since 之后的剩余集；同一 since 下深度
        // 翻页仍会扫描 offset 行，调用方应避免对同一 since 反复翻页
        long offset = (pageCurrent - 1) * size;
        List<FileInfoPo> rows = fileInfoMapper.selectChanges(since, offset, size);
        List<FileAssetAggregate> records = rows.stream().map(FileInfoPersistenceAssembler::toDomain).toList();
        return new FileAssetPage(records, fileInfoMapper.countChanges(since), pageCurrent, size);
    }

    @Override
    public FileRecyclePage pageRecycle(FileRecycleQuery query) {
        long pageCurrent = Math.max(query.current(), 1L);
        long pageSize = Math.max(query.size(), 1L);
        long offset = (pageCurrent - 1) * pageSize;
        List<FileInfoPo> rows = fileInfoMapper.selectRecyclePage(query.originalName(), query.bucket(),
                query.uploaderId(), offset, pageSize);
        List<FileRecycleItem> records = rows.stream().map(FileInfoPersistenceAssembler::toRecycleItem).toList();
        long total = fileInfoMapper.countRecycle(query.originalName(), query.bucket(), query.uploaderId());
        return new FileRecyclePage(records, total, pageCurrent, pageSize);
    }

    @Override
    public List<FileRecycleItem> findExpiredRecycle(LocalDateTime cutoff, long limit) {
        long batchSize = Math.max(limit, 1L);
        return fileInfoMapper.selectExpiredRecycle(cutoff, batchSize).stream()
                .map(FileInfoPersistenceAssembler::toRecycleItem)
                .toList();
    }

    @Override
    public boolean purgeById(Long id) {
        return fileInfoMapper.purgeById(id) > 0;
    }

    @Override
    public List<FileObjectRef> findAllObjectRefs() {
        return fileInfoMapper.selectObjectRefs().stream()
                .map(po -> new FileObjectRef(po.getStorageType(), po.getBucket(), po.getObjectKey()))
                .toList();
    }

    @Override
    public List<FileAssetAggregate> findByStorageType(String storageType, long limit) {
        long batchSize = Math.max(limit, 1L);
        return fileInfoMapper.selectByStorageType(storageType, batchSize).stream()
                .map(FileInfoPersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public void updateStorageType(Long id, String storageType) {
        fileInfoMapper.updateStorageType(id, storageType);
    }

    /**
     * 尽力释放配额命名锁：释放失败仅告警，不影响已提交的业务结果
     *
     * @param lockName 锁名称
     */
    private void releaseQuotaLockQuietly(String lockName) {
        try {
            fileInfoMapper.releaseNamedLock(lockName);
        } catch (RuntimeException e) {
            log.warn("释放文件配额锁失败: lock={}", lockName, e);
        }
    }

    /**
     * 将聚合查询结果转换为字节数，兼容数值与字符串两种驱动返回类型
     *
     * @param value 聚合查询首列值
     * @return 字节数
     * @throws BizException 无法解析时抛出 {@code SYSTEM_ERROR}
     */
    private static long toBytes(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().trim());
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.SYSTEM_ERROR);
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR);
    }
}
