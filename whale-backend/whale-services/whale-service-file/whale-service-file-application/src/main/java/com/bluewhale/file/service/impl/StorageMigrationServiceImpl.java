package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.model.command.StorageMigrationCommand;
import com.bluewhale.file.model.result.StorageMigrationResult;
import com.bluewhale.file.service.StorageMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 存储迁移服务实现：逐个文件从源插件复制到活动插件，校验通过后更新落盘类型。
 *
 * <p><b>一致性</b>：复制过程中同时统计字节数与 MD5，任一与元数据不一致即放弃该文件并清理
 * 目标对象，元数据保持原值；源对象只允许在元数据更新成功后删除。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageMigrationServiceImpl implements StorageMigrationService {

    /** 单批最大处理条数：限制单次请求的 IO 时长 */
    private static final long MAX_BATCH_SIZE = 500L;

    /** 失败样例上限：避免响应体过大 */
    private static final int MAX_FAILURE_SAMPLES = 20;

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final StoragePluginCatalog storagePluginCatalog;

    @Override
    public StorageMigrationResult migrate(StorageMigrationCommand command) {
        String targetType = storagePluginCatalog.activeType();
        String sourceType = command == null || command.sourceStorageType() == null
                ? null : command.sourceStorageType().trim();
        if (!StringUtils.hasText(sourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "源存储类型不能为空");
        }
        if (sourceType.equals(targetType)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "源存储类型不能与当前活动存储类型相同");
        }
        if (!command.dryRun() && !command.confirm()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "迁移操作需显式确认");
        }
        long limit = Math.min(Math.max(command.limit(), 1L), MAX_BATCH_SIZE);
        List<FileAssetAggregate> candidates = fileAssetRepository.findByStorageType(sourceType, limit);
        int migrated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        for (FileAssetAggregate asset : candidates) {
            if (StringUtils.hasText(command.bucket()) && !command.bucket().equals(asset.getBucket())) {
                skipped++;
                continue;
            }
            if (command.dryRun()) {
                migrated++;
                continue;
            }
            try {
                migrateOne(asset, sourceType, targetType, command.deleteSource());
                migrated++;
            } catch (RuntimeException e) {
                failed++;
                if (failures.size() < MAX_FAILURE_SAMPLES) {
                    failures.add(asset.getId() + ":" + e.getMessage());
                }
                log.warn("文件迁移失败，保留原元数据: id={}, bucket={}, objectKey={}", asset.getId(),
                        asset.getBucket(), asset.getObjectKey(), e);
            }
        }
        log.info("存储迁移完成: source={}, target={}, scanned={}, migrated={}, skipped={}, failed={}, dryRun={}",
                sourceType, targetType, candidates.size(), migrated, skipped, failed, command.dryRun());
        return new StorageMigrationResult(sourceType, targetType, candidates.size(), migrated, skipped, failed,
                command.dryRun(), List.copyOf(failures));
    }

    /**
     * 迁移单个文件：复制并校验后更新落盘类型，按需删除源对象
     *
     * @param asset 待迁移文件
     * @param sourceType 源存储类型
     * @param targetType 目标存储类型
     * @param deleteSource 是否删除源对象
     * @throws BizException 读取、写入或校验失败时抛出；目标对象会被尽力清理
     */
    private void migrateOne(FileAssetAggregate asset, String sourceType, String targetType, boolean deleteSource) {
        String bucket = asset.getBucket();
        String objectKey = asset.getObjectKey();
        try {
            try (InputStream raw = fileStorage.load(bucket, objectKey, sourceType);
                 CountingInputStream counting = new CountingInputStream(raw);
                 DigestInputStream digest = new DigestInputStream(counting, md5Digest())) {
                fileStorage.store(bucket, objectKey, digest);
                if (counting.getCount() != asset.getFileSize()) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "迁移字节数与元数据不一致");
                }
                if (asset.getMd5() != null) {
                    String actual = HexFormat.of().formatHex(digest.getMessageDigest().digest());
                    if (!actual.equalsIgnoreCase(asset.getMd5())) {
                        throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "迁移内容摘要与元数据不一致");
                    }
                }
            }
            fileAssetRepository.updateStorageType(asset.getId(), targetType);
        } catch (IOException e) {
            deleteQuietly(bucket, objectKey, targetType);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        } catch (RuntimeException e) {
            deleteQuietly(bucket, objectKey, targetType);
            throw e;
        }
        if (deleteSource) {
            try {
                fileStorage.delete(bucket, objectKey, sourceType);
            } catch (RuntimeException e) {
                log.warn("迁移后删除源对象失败，保留为孤儿待对账清理: bucket={}, objectKey={}, type={}",
                        bucket, objectKey, sourceType, e);
            }
        }
    }

    /**
     * 尽力删除对象：清理失败只告警，不掩盖原始异常
     *
     * @param bucket 桶名称
     * @param objectKey 对象键
     * @param storageType 存储类型
     */
    private void deleteQuietly(String bucket, String objectKey, String storageType) {
        try {
            fileStorage.delete(bucket, objectKey, storageType);
        } catch (RuntimeException e) {
            log.warn("清理迁移目标对象失败，可能残留孤儿文件: bucket={}, objectKey={}, type={}",
                    bucket, objectKey, storageType, e);
        }
    }

    /**
     * 创建 MD5 摘要器
     *
     * @return MD5 摘要器
     * @throws BizException 运行环境不支持 MD5 时抛出 {@code SYSTEM_ERROR}
     */
    private static MessageDigest md5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 统计已读取字节数的输入流包装：用于校验复制完整性
     */
    private static final class CountingInputStream extends FilterInputStream {

        /** 已读取字节数 */
        private long count;

        /**
         * 包装源输入流
         *
         * @param in 源输入流
         */
        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        @Override
        public long skip(long length) throws IOException {
            long skipped = super.skip(length);
            count += skipped;
            return skipped;
        }

        /**
         * 获取已读取字节数
         *
         * @return 已读取字节数
         */
        private long getCount() {
            return count;
        }
    }
}
