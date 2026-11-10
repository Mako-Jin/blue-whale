package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.file.aggregate.FileAsset;
import com.bluewhale.file.domain.file.repository.FileAssetPage;
import com.bluewhale.file.domain.file.repository.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.infrastructure.persistence.assembler.FileInfoPersistenceAssembler;
import com.bluewhale.file.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /** 秒传去重查询的取条数上限：仅需第一条可复用记录 */
    private static final String LIMIT_ONE = "LIMIT 1";

    /** 配额命名锁前缀：按上传用户维度串行化「统计已用量 + 写入」 */
    private static final String QUOTA_LOCK_PREFIX = "whale:file:quota:";

    /** 配额命名锁等待上限（秒）：超时快速失败，避免请求长时间挂起 */
    private static final int QUOTA_LOCK_TIMEOUT_SECONDS = 5;

    private final FileInfoMapper fileInfoMapper;

    @Override
    public FileAsset save(FileAsset asset) {
        FileInfo po = FileInfoPersistenceAssembler.toPo(asset);
        if (po.getId() == null) {
            fileInfoMapper.insert(po);
        } else {
            fileInfoMapper.updateById(po);
        }
        return FileInfoPersistenceAssembler.toDomain(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public FileAsset saveWithQuotaCheck(FileAsset asset, long quotaBytes) {
        Long uploaderId = asset.getUploaderId();
        if (uploaderId == null || quotaBytes <= 0) {
            return save(asset);
        }
        String lockName = QUOTA_LOCK_PREFIX + uploaderId;
        Integer acquired = fileInfoMapper.acquireNamedLock(lockName, QUOTA_LOCK_TIMEOUT_SECONDS);
        if (acquired == null || acquired != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "获取文件配额锁超时，请稍后重试");
        }
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizationActive) {
            // 命名锁绑定当前连接：必须等事务提交/回滚后再释放，避免并发事务在提交前读到旧用量
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    releaseQuotaLockQuietly(lockName);
                }
            });
        }
        try {
            long used = sumNormalSizeByUploader(uploaderId);
            if (used + asset.getFileSize() > quotaBytes) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额");
            }
            return save(asset);
        } finally {
            if (!synchronizationActive) {
                releaseQuotaLockQuietly(lockName);
            }
        }
    }

    @Override
    public Optional<FileAsset> findById(Long id) {
        FileInfo po = fileInfoMapper.selectById(id);
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<FileAsset> findByIdIncludingDeleted(Long id) {
        FileInfo po = fileInfoMapper.selectByIdIncludingDeleted(id);
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<FileAsset> findReusableByDigest(String md5, long fileSize) {
        FileInfo po = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getMd5, md5)
                .eq(FileInfo::getFileSize, fileSize)
                .eq(FileInfo::getStatus, FileStatus.NORMAL.getCode())
                .orderByAsc(FileInfo::getId)
                .last(LIMIT_ONE));
        return Optional.ofNullable(po).map(FileInfoPersistenceAssembler::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        fileInfoMapper.deleteById(id);
    }

    @Override
    public long sumNormalSizeByUploader(Long uploaderId) {
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(file_size), 0) AS total")
                .eq("uploader_id", uploaderId)
                .eq("status", FileStatus.NORMAL.getCode());
        List<Object> values;
        try {
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
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.originalName()),
                FileInfo::getOriginalName, query.originalName());
        wrapper.eq(StringUtils.hasText(query.bucket()), FileInfo::getBucket, query.bucket());
        wrapper.ge(query.createTimeStart() != null, FileInfo::getCreateTime, query.createTimeStart());
        wrapper.le(query.createTimeEnd() != null, FileInfo::getCreateTime, query.createTimeEnd());
        wrapper.eq(query.uploaderId() != null, FileInfo::getUploaderId, query.uploaderId());
        wrapper.orderByDesc(FileInfo::getCreateTime);
        Page<FileInfo> page = fileInfoMapper.selectPage(new Page<>(query.current(), query.size()), wrapper);
        List<FileAsset> records = page.getRecords().stream().map(FileInfoPersistenceAssembler::toDomain).toList();
        return new FileAssetPage(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileAssetPage findChanges(LocalDateTime since, long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        long offset = (pageCurrent - 1) * size;
        List<FileInfo> rows = fileInfoMapper.selectChanges(since, offset, size);
        List<FileAsset> records = rows.stream().map(FileInfoPersistenceAssembler::toDomain).toList();
        return new FileAssetPage(records, fileInfoMapper.countChanges(since), pageCurrent, size);
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
