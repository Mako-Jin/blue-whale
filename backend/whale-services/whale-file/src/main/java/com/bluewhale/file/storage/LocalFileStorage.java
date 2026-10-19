package com.bluewhale.file.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统存储实现：按 {@code ${root}/${bucket}/${objectKey}} 组织落盘。
 *
 * <p>本机未部署对象存储（MinIO），故以本地磁盘作为{@link FileStorage}的落地实现；
 * 定位路径统一做目录穿越防护，拒绝 {@code objectKey} 含 {@code ..} 或归一化后越出根目录。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final FileProperties fileProperties;

    @Override
    public void store(String bucket, String objectKey, InputStream in) {
        Path target = resolve(bucket, objectKey);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public InputStream load(String bucket, String objectKey) {
        Path target = resolve(bucket, objectKey);
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        Path target = resolve(bucket, objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 解析 {@code bucket + objectKey} 对应的落盘路径，并做目录穿越防护。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @return 归一化后的目标文件路径
     * @throws BizException 对象键为空或含 {@code ..}、或归一化结果越出存储根目录时抛出 {@code PARAM_ERROR}
     */
    private Path resolve(String bucket, String objectKey) {
        if (objectKey == null || objectKey.contains("..")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Path root = Paths.get(fileProperties.getStorage().getRoot()).normalize();
        Path target = root.resolve(bucket).resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return target;
    }
}
