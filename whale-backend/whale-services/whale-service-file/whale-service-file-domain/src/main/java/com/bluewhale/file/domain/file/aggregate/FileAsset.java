package com.bluewhale.file.domain.file.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;
import com.bluewhale.file.domain.model.vo.FileStatus;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * FileAsset 聚合根（对应 {@code file_info}）。
 *
 * <p><b>不变量</b>：① {@code bucket} 与 {@code objectKey} 非空；② 新建文件大小必须大于 0；
 * ③ 归档锁定文件不可删除，禁用文件不可锁定。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型；持久化由
 * {@code FileAssetRepository} 端口适配完成。</p>
 *
 * @author BlueWhale
 */
public class FileAsset implements Entity<Long> {

    /** 对象键租户目录前缀：存储层按 {@code tenants/{tenantId}/...} 隔离 */
    private static final String TENANT_KEY_PREFIX = "tenants/";

    /** 原始文件名缺失时的占位名 */
    private static final String UNKNOWN_FILENAME = "unknown";

    /** 对象键分隔符 */
    private static final char PATH_SEPARATOR = '/';

    /** 主键（新建且未落库时为 {@code null}） */
    private final Long id;

    /** 存储文件名（系统按 {@code objectKey} 尾段生成） */
    private final String fileName;

    /** 原始文件名（用户上传时的文件名） */
    private final String originalName;

    /** 对象存储桶名称 */
    private final String bucket;

    /** 对象存储键（桶内完整路径） */
    private final String objectKey;

    /** 文件大小（字节） */
    private final long fileSize;

    /** 文件 MIME 类型，可为 {@code null} */
    private final String contentType;

    /** 文件 MD5 摘要（十六进制小写），可为 {@code null} */
    private final String md5;

    /** 上传用户 ID，可为 {@code null} */
    private final Long uploaderId;

    /** 逻辑删除标记 */
    private final boolean deleted;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 文件状态 */
    private FileStatus status;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量并归一化空值
     *
     * @param id 主键，新建时为 {@code null}
     * @param fileName 存储文件名，为空时由 {@code objectKey} 尾段推导
     * @param originalName 原始文件名，为空时回落占位名
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储键
     * @param fileSize 文件大小（字节）
     * @param contentType MIME 类型，可为 {@code null}
     * @param md5 MD5 摘要，可为 {@code null}
     * @param status 文件状态，为空时按 {@link FileStatus#NORMAL} 处理
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @param deleted 逻辑删除标记
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private FileAsset(Long id, String fileName, String originalName, String bucket, String objectKey,
                      long fileSize, String contentType, String md5, FileStatus status, Long uploaderId,
                      boolean deleted, LocalDateTime createTime, LocalDateTime updateTime) {
        if (bucket == null || bucket.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "对象存储桶名称不能为空");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "对象键不能为空");
        }
        this.id = id;
        this.fileName = fileName == null || fileName.isBlank() ? tailOf(objectKey) : fileName;
        this.originalName = originalName == null || originalName.isBlank() ? UNKNOWN_FILENAME : originalName;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.fileSize = Math.max(fileSize, 0L);
        this.contentType = contentType;
        // 摘要统一归一化为十六进制小写，避免大小写不同导致秒传查询失配或重复登记
        this.md5 = md5 == null || md5.isBlank() ? null : md5.trim().toLowerCase(Locale.ROOT);
        this.status = status == null ? FileStatus.NORMAL : status;
        this.uploaderId = uploaderId;
        this.deleted = deleted;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：登记新上传的文件（无主键，状态为正常）
     *
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储键
     * @param originalName 原始文件名
     * @param fileSize 文件大小（字节）
     * @param contentType MIME 类型，可为 {@code null}
     * @param md5 MD5 摘要（十六进制小写）
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @return 新建的文件资产聚合根
     * @throws DomainException 原始文件名为空或文件大小非正数时抛出 {@code PARAM_ERROR}
     */
    public static FileAsset register(String bucket, String objectKey, String originalName, long fileSize,
                                     String contentType, String md5, Long uploaderId) {
        if (originalName == null || originalName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "原始文件名不能为空");
        }
        if (fileSize <= 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件大小必须大于 0");
        }
        LocalDateTime now = LocalDateTime.now();
        return new FileAsset(null, null, originalName, bucket, objectKey, fileSize, contentType, md5,
                FileStatus.NORMAL, uploaderId, false, now, now);
    }

    /**
     * 工厂方法：秒传登记（复用既有物理对象，仅新建元数据）
     *
     * @param source 命中的既有文件资产（提供桶、对象键、大小与摘要）
     * @param originalName 本次上传的原始文件名
     * @param contentType 本次请求的 MIME 类型；为空时沿用 {@code source} 的类型
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @return 新建的文件资产聚合根
     * @throws DomainException 源文件资产为空时抛出 {@code SYSTEM_ERROR}
     */
    public static FileAsset reuse(FileAsset source, String originalName, String contentType, Long uploaderId) {
        if (source == null) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "复用的文件对象不能为空");
        }
        String resolvedContentType = contentType == null || contentType.isBlank()
                ? source.contentType : contentType;
        LocalDateTime now = LocalDateTime.now();
        return new FileAsset(null, source.fileName, originalName, source.bucket, source.objectKey, source.fileSize,
                resolvedContentType, source.md5, FileStatus.NORMAL, uploaderId, false, now, now);
    }

    /**
     * 重建方法：由持久化数据还原文件资产聚合根
     *
     * @param id 主键
     * @param fileName 存储文件名
     * @param originalName 原始文件名
     * @param bucket 对象存储桶名称
     * @param objectKey 对象存储键
     * @param fileSize 文件大小（字节）
     * @param contentType MIME 类型，可为 {@code null}
     * @param md5 MD5 摘要，可为 {@code null}
     * @param status 文件状态
     * @param uploaderId 上传用户 ID，可为 {@code null}
     * @param deleted 逻辑删除标记
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的文件资产聚合根
     */
    public static FileAsset reconstitute(Long id, String fileName, String originalName, String bucket,
                                         String objectKey, long fileSize, String contentType, String md5,
                                         FileStatus status, Long uploaderId, boolean deleted,
                                         LocalDateTime createTime, LocalDateTime updateTime) {
        return new FileAsset(id, fileName, originalName, bucket, objectKey, fileSize, contentType, md5,
                status, uploaderId, deleted, createTime, updateTime);
    }

    /**
     * 行为：归档锁定文件
     *
     * <p>幂等：已锁定返回 {@code false} 且不改变状态；禁用文件不可锁定。</p>
     *
     * @return 状态是否发生变化（需要持久化）
     * @throws DomainException 文件已禁用时抛出 {@code PARAM_ERROR}
     */
    public boolean lock() {
        if (status == FileStatus.LOCKED) {
            return false;
        }
        if (status != FileStatus.NORMAL) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件已禁用，不可锁定");
        }
        this.status = FileStatus.LOCKED;
        touch();
        return true;
    }

    /**
     * 行为：解除归档锁定，状态恢复为正常
     *
     * <p>幂等：未锁定文件返回 {@code false} 且不改变状态。</p>
     *
     * @return 状态是否发生变化（需要持久化）
     */
    public boolean unlock() {
        if (status != FileStatus.LOCKED) {
            return false;
        }
        this.status = FileStatus.NORMAL;
        touch();
        return true;
    }

    /**
     * 行为：删除前校验聚合可删除性
     *
     * @throws DomainException 文件已归档锁定时抛出 {@code PARAM_ERROR}
     */
    public void assertDeletable() {
        if (status == FileStatus.LOCKED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件已归档锁定，禁止删除");
        }
    }

    /**
     * 判断文件是否对指定租户可见：以对象键租户前缀为准，无租户上下文时不做限制
     *
     * @param tenantId 当前租户 ID，可为 {@code null}
     * @return 可见时返回 {@code true}
     */
    public boolean isVisibleToTenant(Long tenantId) {
        if (tenantId == null) {
            return true;
        }
        return objectKey.startsWith(TENANT_KEY_PREFIX + tenantId + PATH_SEPARATOR);
    }

    /**
     * 行为：校验请求者可见性——跨租户不可见；管理权限可访问全租户文件；其余仅上传者本人可访问。
     *
     * <p>服务间上传（上传者为空）不向普通用户开放，仅管理视角与无用户态的内网调用可访问；
     * 不可见时统一按不存在处理，避免泄露文件是否存在。</p>
     *
     * @param requesterId 调用者用户 ID，可为 {@code null}（内网服务调用）
     * @param canManage 是否持有文件管理权限
     * @param tenantId 当前租户 ID，可为 {@code null}
     * @throws DomainException 不可见时抛出 {@code NOT_FOUND}
     */
    public void assertVisibleTo(Long requesterId, boolean canManage, Long tenantId) {
        assertVisibleToTenant(tenantId);
        if (canManage || Objects.equals(uploaderId, requesterId)) {
            return;
        }
        throw new DomainException(ErrorCode.NOT_FOUND);
    }

    /**
     * 行为：校验文件对指定租户可见，跨租户按不存在处理
     *
     * @param tenantId 当前租户 ID，可为 {@code null}
     * @throws DomainException 跨租户时抛出 {@code NOT_FOUND}
     */
    public void assertVisibleToTenant(Long tenantId) {
        if (!isVisibleToTenant(tenantId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * 行为：刷新更新时间
     */
    private void touch() {
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 提取对象键尾段作为存储文件名
     *
     * @param objectKey 对象存储键
     * @return 对象键尾段
     */
    private static String tailOf(String objectKey) {
        int index = objectKey.lastIndexOf(PATH_SEPARATOR);
        return index >= 0 ? objectKey.substring(index + 1) : objectKey;
    }

    /**
     * 获取主键
     *
     * @return 主键（新建且未落库时为 {@code null}）
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取存储文件名
     *
     * @return 存储文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 获取原始文件名
     *
     * @return 原始文件名
     */
    public String getOriginalName() {
        return originalName;
    }

    /**
     * 获取对象存储桶名称
     *
     * @return 桶名称
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 获取对象存储键
     *
     * @return 对象键
     */
    public String getObjectKey() {
        return objectKey;
    }

    /**
     * 获取文件大小
     *
     * @return 文件大小（字节）
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * 获取 MIME 类型
     *
     * @return MIME 类型，可为 {@code null}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 获取 MD5 摘要
     *
     * @return MD5 摘要（十六进制小写），可为 {@code null}
     */
    public String getMd5() {
        return md5;
    }

    /**
     * 获取文件状态
     *
     * @return 文件状态
     */
    public FileStatus getStatus() {
        return status;
    }

    /**
     * 获取上传用户 ID
     *
     * @return 上传用户 ID，可为 {@code null}
     */
    public Long getUploaderId() {
        return uploaderId;
    }

    /**
     * 判断是否已逻辑删除
     *
     * @return 已逻辑删除时返回 {@code true}
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
