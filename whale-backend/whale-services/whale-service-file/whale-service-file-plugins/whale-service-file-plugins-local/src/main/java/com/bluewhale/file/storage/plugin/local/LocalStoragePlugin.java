package com.bluewhale.file.storage.plugin.local;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.storage.StoragePlugin;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统存储插件：按 {@code ${root}/${bucket}/${objectKey}} 组织落盘。
 *
 * <p>定位路径时拒绝桶名/对象键中的 {@code ..} 路径段，并在归一化后校验未越出根目录；
 * 不支持预签名下载，由服务侧在调用前根据 {@link #supportsPresigned()} 拦截。
 * 输入/输出流的归属遵循 {@link StoragePlugin} 契约：{@code store} 不关闭入参流，
 * {@code load} 返回的流由调用方关闭。</p>
 *
 * @author BlueWhale
 */
public class LocalStoragePlugin implements StoragePlugin {

    /** 插件类型标识 */
    public static final String TYPE = "local";

    private final Path root;

    /**
     * 以配置构造本地存储插件，并确保根目录存在。
     *
     * @param properties 本地存储配置
     * @throws BizException 未配置根目录或目录创建失败时抛出 {@code SYSTEM_ERROR}
     */
    public LocalStoragePlugin(LocalStorageProperties properties) {
        if (!StringUtils.hasText(properties.getRoot())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "未配置本地存储根目录: whale.file.storage.root");
        }
        this.root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "本地存储根目录创建失败: " + root);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "本地文件系统";
    }

    @Override
    public boolean supportsPresigned() {
        return false;
    }

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

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "本地存储插件不支持预签名下载");
    }

    /**
     * 解析 {@code bucket + objectKey} 对应的落盘路径，并做目录穿越防护。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @return 归一化后的目标文件路径
     * @throws BizException 桶名/对象键为空、含 {@code ..} 路径段，或归一化结果越出存储根目录时
     *                      抛出 {@code PARAM_ERROR}
     */
    private Path resolve(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存储桶名与对象键不能为空");
        }
        // 只拒绝段级 ..（如 a/../b），允许 report..2024.pdf 这类含连续点的合法文件名
        if (hasParentSegment(bucket) || hasParentSegment(objectKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Path target = root.resolve(bucket).resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return target;
    }

    /**
     * 判断路径是否含 {@code ..} 段（同时兼容 Windows 的 {@code \} 分隔符）。
     *
     * @param path 桶名或对象键
     * @return 含父目录段时返回 {@code true}
     */
    private static boolean hasParentSegment(String path) {
        for (String segment : path.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
