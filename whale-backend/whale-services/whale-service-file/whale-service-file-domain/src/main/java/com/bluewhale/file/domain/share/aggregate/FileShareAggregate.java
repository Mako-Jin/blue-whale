package com.bluewhale.file.domain.share.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;
import com.bluewhale.file.domain.share.model.vo.SharePassword;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * FileShare 聚合根（对应 {@code file_share}）。
 *
 * <p><b>不变量</b>：① 被分享文件与令牌非空；② 有效期必须晚于创建时间；
 * ③ 下载次数上限为空表示不限，非空必须大于 0；④ 密码只保存哈希。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型；随机令牌与密码哈希均为 JDK 能力。</p>
 *
 * @author BlueWhale
 */
@Getter
public class FileShareAggregate implements Entity<Long> {

    /** 失效提示统一口径：不区分过期、撤销、超次数，避免泄露分享状态 */
    private static final String INACTIVE_MESSAGE = "分享链接不存在或已失效";

    /** 主键（新建且未落库时为 {@code null}） */
    private final Long id;

    /** 被分享的文件ID */
    private final Long fileId;

    /** 创建时的原始文件名快照（文件后续重命名不影响分享列表展示口径） */
    private final String fileName;

    /** 分享令牌原文 */
    private final String token;

    /** 密码哈希；为 {@code null} 表示无密码 */
    private final String passwordHash;

    /** 失效时间；为 {@code null} 表示长期有效 */
    private final LocalDateTime expireTime;

    /** 下载次数上限；为 {@code null} 表示不限 */
    private final Integer maxDownloads;

    /** 已下载次数 */
    private final int downloadCount;

    /** 分享状态 */
    private ShareStatus status;

    /** 创建人用户 ID，可为 {@code null}（历史数据） */
    private final Long creatorId;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 更新时间 */
    private final LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量并归一化空值
     *
     * @param id 主键，新建时为 {@code null}
     * @param fileId 被分享的文件ID
     * @param fileName 原始文件名快照
     * @param token 分享令牌原文
     * @param passwordHash 密码哈希，可为 {@code null}
     * @param expireTime 失效时间，可为 {@code null}
     * @param maxDownloads 下载次数上限，可为 {@code null}
     * @param downloadCount 已下载次数
     * @param status 分享状态
     * @param creatorId 创建人用户 ID
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private FileShareAggregate(Long id, Long fileId, String fileName, String token, String passwordHash,
                               LocalDateTime expireTime, Integer maxDownloads, int downloadCount,
                               ShareStatus status, Long creatorId, LocalDateTime createTime,
                               LocalDateTime updateTime) {
        if (fileId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享文件ID不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享文件名不能为空");
        }
        this.id = id;
        this.fileId = fileId;
        this.fileName = fileName;
        this.token = ShareToken.of(token).value();
        this.passwordHash = passwordHash == null || passwordHash.isBlank() ? null : passwordHash;
        this.expireTime = expireTime;
        this.maxDownloads = maxDownloads == null || maxDownloads <= 0 ? null : maxDownloads;
        this.downloadCount = Math.max(downloadCount, 0);
        this.status = status == null ? ShareStatus.ACTIVE : status;
        this.creatorId = creatorId;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：创建分享（令牌由调用方生成，便于幂等重试时复用）
     *
     * @param fileId 被分享的文件ID
     * @param fileName 原始文件名快照
     * @param token 分享令牌
     * @param passwordHash 密码哈希，可为 {@code null}
     * @param expireTime 失效时间，可为 {@code null}
     * @param maxDownloads 下载次数上限，{@code null} 或 {@code <=0} 表示不限
     * @param creatorId 创建人用户 ID
     * @return 新建的分享聚合根
     */
    public static FileShareAggregate create(Long fileId, String fileName, String token, String passwordHash,
                                            LocalDateTime expireTime, Integer maxDownloads, Long creatorId) {
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && !expireTime.isAfter(now)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "分享失效时间必须晚于当前时间");
        }
        return new FileShareAggregate(null, fileId, fileName, token, passwordHash, expireTime, maxDownloads,
                0, ShareStatus.ACTIVE, creatorId, now, now);
    }

    /**
     * 重建方法：由持久化数据还原分享聚合根
     *
     * @param id 主键
     * @param fileId 被分享的文件ID
     * @param fileName 原始文件名快照
     * @param token 分享令牌
     * @param passwordHash 密码哈希，可为 {@code null}
     * @param expireTime 失效时间，可为 {@code null}
     * @param maxDownloads 下载次数上限，可为 {@code null}
     * @param downloadCount 已下载次数
     * @param status 分享状态
     * @param creatorId 创建人用户 ID
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的分享聚合根
     */
    public static FileShareAggregate reconstitute(Long id, Long fileId, String fileName, String token,
                                                  String passwordHash, LocalDateTime expireTime,
                                                  Integer maxDownloads, int downloadCount, ShareStatus status,
                                                  Long creatorId, LocalDateTime createTime,
                                                  LocalDateTime updateTime) {
        return new FileShareAggregate(id, fileId, fileName, token, passwordHash, expireTime, maxDownloads,
                downloadCount, status, creatorId, createTime, updateTime);
    }

    /**
     * 判断分享是否需要密码
     *
     * @return 需要密码时返回 {@code true}
     */
    public boolean requiresPassword() {
        return passwordHash != null;
    }

    /**
     * 判断分享是否已过期
     *
     * @param now 当前时间
     * @return 已过期时返回 {@code true}
     */
    public boolean isExpired(LocalDateTime now) {
        return expireTime != null && !expireTime.isAfter(now);
    }

    /**
     * 判断下载次数是否已耗尽
     *
     * @return 已耗尽时返回 {@code true}
     */
    public boolean isExhausted() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }

    /**
     * 行为：校验分享当前可访问（状态、有效期、下载次数）
     *
     * @param now 当前时间
     * @throws DomainException 任一条件不满足时抛出 {@code NOT_FOUND}
     */
    public void assertAccessible(LocalDateTime now) {
        if (status != ShareStatus.ACTIVE || isExpired(now) || isExhausted()) {
            throw new DomainException(ErrorCode.NOT_FOUND, INACTIVE_MESSAGE);
        }
    }

    /**
     * 行为：校验分享密码
     *
     * <p>无密码分享直接通过；密码错误与缺失统一抛 {@code FORBIDDEN}，
     * 但提示区分，便于前端引导用户输入。</p>
     *
     * @param rawPassword 访问者提供的明文密码，可为 {@code null}
     * @throws DomainException 需要密码但未提供或密码错误时抛出 {@code FORBIDDEN}
     */
    public void assertPassword(String rawPassword) {
        if (!requiresPassword()) {
            return;
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "需要分享密码");
        }
        if (!SharePassword.matches(rawPassword, passwordHash)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "分享密码错误");
        }
    }

    /**
     * 行为：撤销分享
     *
     * <p>幂等：已撤销返回 {@code false} 且不改变状态。</p>
     *
     * @return 状态是否发生变化（需要持久化）
     */
    public boolean revoke() {
        if (status != ShareStatus.ACTIVE) {
            return false;
        }
        this.status = ShareStatus.REVOKED;
        return true;
    }

    /**
     * 行为：校验请求者是否有权管理该分享（创建人本人或文件管理权限）
     *
     * <p>不可见时统一按不存在处理，避免泄露分享是否存在。</p>
     *
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @throws DomainException 无权限时抛出 {@code NOT_FOUND}
     */
    public void assertManageableBy(Long requesterId, boolean canManage) {
        if (canManage || Objects.equals(creatorId, requesterId)) {
            return;
        }
        throw new DomainException(ErrorCode.NOT_FOUND);
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
        if (!(other instanceof FileShareAggregate share)) {
            return false;
        }
        return id != null && id.equals(share.id);
    }

    /**
     * 身份哈希：与 {@link #equals(Object)} 保持一致
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
