package com.bluewhale.file.domain.file.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;
import com.bluewhale.file.domain.model.vo.FileStatus;
import lombok.Getter;

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
@Getter
public class FileAssetAggregate implements Entity<Long> {

    /** 对象键租户目录前缀：存储层按 {@code tenants/{tenantId}/...} 隔离 */
    private static final String TENANT_KEY_PREFIX = "tenants/";

    /** 原始文件名缺失时的占位名 */
    private static final String UNKNOWN_FILENAME = "unknown";

    /** 对象键分隔符 */
    private static final char PATH_SEPARATOR = '/';

    /** 主键（新建且未落库时为 {@code null}）
     * -- GETTER --
     *  获取主键
     *
     * @return 主键（新建且未落库时为 {@code null}）
     */
    private final Long id;

    /** 存储文件名（系统按 {@code objectKey} 尾段生成）
     * -- GETTER --
     *  获取存储文件名
     *
     * @return 存储文件名
     */
    private final String fileName;

    /** 原始文件名（用户上传时的文件名，可由重命名行为修改）
     * -- GETTER --
     *  获取原始文件名
     *
     * @return 原始文件名
     */
    private String originalName;

    /** 对象存储桶名称（可由跨桶移动行为修改）
     * -- GETTER --
     *  获取对象存储桶名称
     *
     * @return 桶名称
     */
    private String bucket;

    /** 落盘时的存储插件类型（如 {@code local}、{@code minio}），历史数据可为 {@code null}
     * -- GETTER --
     *  获取落盘时的存储插件类型
     *
     * @return 存储插件类型，可为 {@code null}
     */
    private final String storageType;

    /** 对象存储键（桶内完整路径）
     * -- GETTER --
     *  获取对象存储键
     *
     * @return 对象键
     */
    private final String objectKey;

    /** 文件大小（字节）
     * -- GETTER --
     *  获取文件大小
     *
     * @return 文件大小（字节）
     */
    private final long fileSize;

    /** 文件 MIME 类型，可为 {@code null}
     * -- GETTER --
     *  获取 MIME 类型
     *
     * @return MIME 类型，可为 {@code null}
     */
    private final String contentType;

    /** 文件 MD5 摘要（十六进制小写），可为 {@code null}
     * -- GETTER --
     *  获取 MD5 摘要
     *
     * @return MD5 摘要（十六进制小写），可为 {@code null}
     */
    private final String md5;

    /** 上传用户 ID，可为 {@code null}
     * -- GETTER --
     *  获取上传用户 ID
     *
     * @return 上传用户 ID，可为 {@code null}
     */
    private final Long uploaderId;

    /** 逻辑删除标记
     * -- GETTER --
     *  判断是否已逻辑删除
     *
     * @return 已逻辑删除时返回 {@code true}
     */
    private final boolean deleted;

    /** 创建时间
     * -- GETTER --
     *  获取创建时间
     *
     * @return 创建时间
     */
    private final LocalDateTime createTime;

    /** 文件状态
     * -- GETTER --
     *  获取文件状态
     *
     * @return 文件状态
     */
    private FileStatus status;

    /** 更新时间
     * -- GETTER --
     *  获取更新时间
     *
     * @return 更新时间
     */
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
     * @param storageType 落盘时的存储插件类型，可为 {@code null}
     */
    private FileAssetAggregate(Long id, String fileName, String originalName, String bucket, String objectKey,
                               long fileSize, String contentType, String md5, FileStatus status, Long uploaderId,
                               boolean deleted, LocalDateTime createTime, LocalDateTime updateTime, String storageType) {
        if (bucket == null || bucket.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "对象存储桶名称不能为空");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "对象键不能为空");
        }
        if (fileSize < 0) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "文件大小不能为负数: " + fileSize);
        }
        this.id = id;
        this.fileName = fileName == null || fileName.isBlank() ? tailOf(objectKey) : fileName;
        this.originalName = originalName == null || originalName.isBlank() ? UNKNOWN_FILENAME : originalName;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.storageType = storageType == null || storageType.isBlank() ? null : storageType.trim();
        this.fileSize = fileSize;
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
     * @param storageType 落盘时的存储插件类型
     * @return 新建的文件资产聚合根
     * @throws DomainException 原始文件名为空或文件大小非正数时抛出 {@code PARAM_ERROR}
     */
    public static FileAssetAggregate register(String bucket, String objectKey, String originalName, long fileSize,
                                              String contentType, String md5, Long uploaderId, String storageType) {
        if (originalName == null || originalName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "原始文件名不能为空");
        }
        if (fileSize <= 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件大小必须大于 0");
        }
        LocalDateTime now = LocalDateTime.now();
        return new FileAssetAggregate(null, null, originalName, bucket, objectKey, fileSize, contentType, md5,
                FileStatus.NORMAL, uploaderId, false, now, now, storageType);
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
    public static FileAssetAggregate reuse(FileAssetAggregate source, String originalName, String contentType, Long uploaderId) {
        if (source == null) {
            throw new DomainException(ErrorCode.SYSTEM_ERROR, "复用的文件对象不能为空");
        }
        String resolvedContentType = contentType == null || contentType.isBlank()
                ? source.contentType : contentType;
        LocalDateTime now = LocalDateTime.now();
        return new FileAssetAggregate(null, source.fileName, originalName, source.bucket, source.objectKey, source.fileSize,
                resolvedContentType, source.md5, FileStatus.NORMAL, uploaderId, false, now, now, source.storageType);
    }

    /**
     * 重建方法：由持久化数据还原文件资产聚合根
     *
     * <p>大小语义与新建不同：允许 {@code fileSize = 0} 以兼容存量空文件；负数视为数据异常，
     * 抛 {@code SYSTEM_ERROR} 而非静默归一。</p>
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
     * @param storageType 落盘时的存储插件类型，可为 {@code null}
     * @return 还原的文件资产聚合根
     */
    public static FileAssetAggregate reconstitute(Long id, String fileName, String originalName, String bucket,
                                                  String objectKey, long fileSize, String contentType, String md5,
                                                  FileStatus status, Long uploaderId, boolean deleted,
                                                  LocalDateTime createTime, LocalDateTime updateTime, String storageType) {
        return new FileAssetAggregate(id, fileName, originalName, bucket, objectKey, fileSize, contentType, md5,
                status, uploaderId, deleted, createTime, updateTime, storageType);
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
     * <p>幂等：未锁定文件返回 {@code false} 且不改变状态；禁用文件不属于可锁定状态，
     * 解除锁定视为非法操作，与 {@link #lock()} 的行为保持对称。</p>
     *
     * @return 状态是否发生变化（需要持久化）
     * @throws DomainException 文件已禁用时抛出 {@code PARAM_ERROR}
     */
    public boolean unlock() {
        if (status == FileStatus.DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件已禁用，不可解除锁定");
        }
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
     * <p>仅归档锁定文件禁止删除；禁用只限制使用与锁定，不限制删除。</p>
     *
     * @throws DomainException 文件已归档锁定时抛出 {@code PARAM_ERROR}
     */
    public void assertDeletable() {
        if (status == FileStatus.LOCKED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件已归档锁定，禁止删除");
        }
    }

    /**
     * 行为：创建分享前校验文件可用性
     *
     * <p>禁用文件不可对外分享；归档锁定只禁止内容变更与删除，不限制只读分享。</p>
     *
     * @throws DomainException 文件已禁用时抛出 {@code PARAM_ERROR}
     */
    public void assertShareable() {
        if (status == FileStatus.DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件已禁用，不可分享");
        }
    }

    /**
     * 行为：重命名原始文件名（不改变存储文件名与对象键）
     *
     * <p>仅正常状态可重命名；禁用与归档锁定代表元数据不可变，统一拒绝。</p>
     *
     * @param newName 新的原始文件名
     * @return 是否发生变化（需要持久化）；与当前名称相同返回 {@code false}
     * @throws DomainException 名称为空或文件非正常状态时抛出 {@code PARAM_ERROR}
     */
    public boolean rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "原始文件名不能为空");
        }
        assertMutable("重命名");
        String normalized = newName.trim();
        if (normalized.equals(this.originalName)) {
            return false;
        }
        this.originalName = normalized;
        touch();
        return true;
    }

    /**
     * 行为：移动到目标桶（对象键不变）
     *
     * <p>仅正常状态可移动；桶名拒绝路径分隔符与父目录段，避免拼接出越权路径。
     * 物理对象迁移由应用层先于本行为完成（或在其失败时回迁）。</p>
     *
     * @param targetBucket 目标桶名称
     * @return 是否发生变化（需要持久化）；目标与当前相同返回 {@code false}
     * @throws DomainException 桶名为空、含非法字符或文件非正常状态时抛出 {@code PARAM_ERROR}
     */
    public boolean moveTo(String targetBucket) {
        if (targetBucket == null || targetBucket.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "目标桶名称不能为空");
        }
        assertMutable("移动");
        String normalized = targetBucket.trim();
        if (normalized.indexOf(PATH_SEPARATOR) >= 0 || normalized.indexOf('\\') >= 0
                || hasParentSegment(normalized)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "目标桶名称非法");
        }
        if (normalized.equals(this.bucket)) {
            return false;
        }
        this.bucket = normalized;
        touch();
        return true;
    }

    /**
     * 校验文件元数据是否可变：仅正常状态允许改名/移动
     *
     * @param operation 操作名称，用于异常信息
     * @throws DomainException 文件非正常状态时抛出 {@code PARAM_ERROR}
     */
    private void assertMutable(String operation) {
        if (status != FileStatus.NORMAL) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "文件当前状态不允许" + operation);
        }
    }

    /**
     * 判断桶名是否含 {@code ..} 段（同时兼容 Windows 的 {@code \} 分隔符）
     *
     * @param bucket 桶名
     * @return 含父目录段时返回 {@code true}
     */
    private static boolean hasParentSegment(String bucket) {
        for (String segment : bucket.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是否对指定租户可见：以对象键租户前缀为准，无租户上下文时不做限制
     *
     * <p>前缀约定为 {@code tenants/{tenantId}/}（含结尾分隔符），由对象键生成侧
     * （{@code buildObjectKey}）保证；领域层只做前缀匹配，不解析键结构。</p>
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
     * <p>{@code requesterId} 为 {@code null} 且 {@code canManage} 为 {@code false} 时，仅能访问
     * 上传者为空的服务间上传文件；内网系统级访问用户文件必须显式传 {@code canManage=true}。</p>
     *
     * @param requesterId 调用者用户 ID，可为 {@code null}（内网服务调用）
     * @param canManage 是否持有文件管理权限
     * @param tenantId 当前租户 ID，可为 {@code null}
     * @throws DomainException 不可见时抛出 {@code NOT_FOUND}
     */
    public void assertVisibleTo(Long requesterId, boolean canManage, Long tenantId) {
        assertVisibleToTenant(tenantId);
        if (canManage || Objects.equals(this.uploaderId, requesterId)) {
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
     * 身份相等性：已落库实体按主键比较，新建未落库实体退化为对象同一性
     *
     * @param other 待比较对象
     * @return 同一实体时返回 {@code true}
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileAssetAggregate)) {
            return false;
        }
        FileAssetAggregate asset = (FileAssetAggregate) other;
        // 任一未落库则不按主键比较，退化为对象同一性（上方已处理 this == other）
        if (id == null || asset.id == null) {
            return false;
        }
        return id.equals(asset.id);
    }

    /**
     * 身份哈希：与 {@link #equals(Object)} 保持一致，未落库实体使用对象标识哈希
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
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

}
