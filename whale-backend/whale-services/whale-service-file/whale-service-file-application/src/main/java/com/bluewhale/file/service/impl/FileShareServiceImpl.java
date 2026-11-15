package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileShareItem;
import com.bluewhale.file.domain.share.model.FileSharePage;
import com.bluewhale.file.domain.share.model.FileShareQuery;
import com.bluewhale.file.domain.share.model.vo.SharePassword;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.metrics.FileMetrics;
import com.bluewhale.file.model.command.CreateShareCommand;
import com.bluewhale.file.model.query.FileSharePageQuery;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareCreateResult;
import com.bluewhale.file.model.result.FileShareResult;
import com.bluewhale.file.service.FileShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享服务实现。
 *
 * <p><b>安全边界</b>：令牌随机生成并持久化，不可枚举；密码只存 PBKDF2 哈希；
 * 访问异常统一按「不存在」返回，避免泄露分享状态；下载次数占用由存储层原子条件更新，
 * 并发下不会超发。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileShareServiceImpl implements FileShareService {

    /** 失效提示统一口径：不区分过期、撤销、超次数 */
    private static final String INACTIVE_MESSAGE = "分享链接不存在或已失效";

    /** 单页最大条数：与文件列表保持一致 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final FileShareRepository fileShareRepository;

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final FileProperties fileProperties;

    private final FileMetrics fileMetrics;

    @Override
    public FileShareCreateResult create(CreateShareCommand command) {
        FileAssetAggregate asset = fileAssetRepository.findById(command.fileId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        asset.assertVisibleTo(command.requesterId(), command.canManage(), TenantContext.get());
        asset.assertShareable();
        String passwordHash = StringUtils.hasText(command.password())
                ? SharePassword.hash(command.password()) : null;
        FileShareAggregate share = FileShareAggregate.create(asset.getId(), asset.getOriginalName(),
                ShareToken.generate().value(), passwordHash, resolveExpireTime(command.expireHours()),
                command.maxDownloads(), command.requesterId());
        try {
            FileShareAggregate saved = fileShareRepository.save(share);
            fileMetrics.recordShareCreate(true);
            return new FileShareCreateResult(saved.getId(), saved.getFileId(), saved.getToken(),
                    saved.getExpireTime(), saved.getMaxDownloads(), saved.requiresPassword());
        } catch (RuntimeException e) {
            fileMetrics.recordShareCreate(false);
            throw e;
        }
    }

    @Override
    public PageResult<FileShareResult> page(FileSharePageQuery query) {
        long pageCurrent = Math.max(query.current(), 1L);
        long pageSize = Math.min(Math.max(query.size(), 1L), MAX_PAGE_SIZE);
        Long creatorFilter = query.canManage() ? null : query.requesterId();
        FileSharePage page = fileShareRepository.page(new FileShareQuery(creatorFilter, pageCurrent, pageSize));
        List<FileShareResult> records = page.records().stream().map(FileShareServiceImpl::toResult).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    public void revoke(Long id, Long requesterId, boolean canManage) {
        FileShareAggregate share = fileShareRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        share.assertManageableBy(requesterId, canManage);
        if (share.revoke()) {
            fileShareRepository.revoke(id);
        }
    }

    @Override
    public FileShareAccessResult access(String token, String password) {
        FileShareAggregate share = requireShare(token);
        share.assertAccessible(LocalDateTime.now());
        FileAssetAggregate asset = requireSharedFile(share);
        if (share.requiresPassword() && !StringUtils.hasText(password)) {
            return new FileShareAccessResult(null, true, share.getExpireTime(), share.getMaxDownloads(),
                    share.getDownloadCount());
        }
        share.assertPassword(password);
        return new FileShareAccessResult(toInfoResult(asset), false, share.getExpireTime(),
                share.getMaxDownloads(), share.getDownloadCount());
    }

    @Override
    public InputStream openContent(String token, String password, Long offset, Long length) {
        FileShareAggregate share = requireShare(token);
        share.assertAccessible(LocalDateTime.now());
        share.assertPassword(password);
        FileAssetAggregate asset = requireSharedFile(share);
        if (!fileShareRepository.consumeDownload(share.getId())) {
            fileMetrics.recordShareDownload(false);
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), INACTIVE_MESSAGE);
        }
        try {
            InputStream content = offset == null || length == null
                    ? fileStorage.load(asset.getBucket(), asset.getObjectKey(), asset.getStorageType())
                    : fileStorage.load(asset.getBucket(), asset.getObjectKey(), offset, length,
                            asset.getStorageType());
            fileMetrics.recordShareDownload(true);
            return content;
        } catch (RuntimeException e) {
            fileMetrics.recordShareDownload(false);
            throw e;
        }
    }

    /**
     * 按令牌加载分享；令牌格式非法或不存在时统一按失效处理
     *
     * @param token 分享令牌
     * @return 分享聚合根
     * @throws BizException 令牌非法或不存在时抛出 {@code NOT_FOUND}
     */
    private FileShareAggregate requireShare(String token) {
        String normalized;
        try {
            normalized = ShareToken.of(token).value();
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), INACTIVE_MESSAGE);
        }
        return fileShareRepository.findByToken(normalized)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND.getCode(), INACTIVE_MESSAGE));
    }

    /**
     * 加载分享指向的文件并校验可用性
     *
     * @param share 分享聚合根
     * @return 文件资产聚合根
     * @throws BizException 文件已删除或已禁用时抛出 {@code NOT_FOUND}
     */
    private FileAssetAggregate requireSharedFile(FileShareAggregate share) {
        FileAssetAggregate asset = fileAssetRepository.findById(share.getFileId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND.getCode(), INACTIVE_MESSAGE));
        asset.assertVisibleToTenant(TenantContext.get());
        if (asset.getStatus() == FileStatus.DISABLED) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), INACTIVE_MESSAGE);
        }
        return asset;
    }

    /**
     * 解析分享失效时间：缺省取配置默认值，超过上限截断
     *
     * @param expireHours 请求的有效小时数，可为 {@code null}
     * @return 失效时间
     * @throws BizException 有效期非正数时抛出 {@code PARAM_ERROR}
     */
    private LocalDateTime resolveExpireTime(Integer expireHours) {
        FileProperties.Share config = fileProperties.getShare();
        long hours = expireHours == null ? config.getDefaultExpireHours() : expireHours;
        if (hours <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分享有效期必须大于 0 小时");
        }
        return LocalDateTime.now().plusHours(Math.min(hours, config.getMaxExpireHours()));
    }

    /**
     * 分享投影转结果
     *
     * @param item 分享投影
     * @return 分享结果
     */
    private static FileShareResult toResult(FileShareItem item) {
        return new FileShareResult(item.id(), item.fileId(), item.fileName(), item.token(),
                item.expireTime(), item.maxDownloads(), item.downloadCount(), item.status(),
                item.createTime());
    }

    /**
     * 文件资产转元数据结果（分享访问响应头复用）
     *
     * @param asset 文件资产聚合根
     * @return 文件元数据
     */
    private static FileInfoResult toInfoResult(FileAssetAggregate asset) {
        return new FileInfoResult(asset.getId(), asset.getFileName(), asset.getOriginalName(),
                asset.getBucket(), asset.getObjectKey(), asset.getFileSize(), asset.getContentType(),
                asset.getMd5(), asset.getUploaderId(), asset.getCreateTime());
    }
}
