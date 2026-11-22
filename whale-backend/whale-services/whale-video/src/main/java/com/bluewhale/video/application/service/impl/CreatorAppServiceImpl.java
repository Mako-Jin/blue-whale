package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.video.application.command.CreatorQueryCommand;
import com.bluewhale.video.application.command.CreatorUpdateCommand;
import com.bluewhale.video.application.service.CreatorAppService;
import com.bluewhale.video.application.view.CreatorAdminView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.CreatorQuery;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.FileAssetPort;
import com.bluewhale.video.domain.service.VideoUploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 创作者运营应用服务实现。
 *
 * <p>创作者由上传流程自动登记，运营侧在此维护昵称 / 简介 / 头像与启用状态；
 * 停用后创作者不得再发布新内容。头像通过 {@code /video/creator/{id}/avatar}
 * 代理访问，运营视图同样不暴露头像文件 ID。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CreatorAppServiceImpl implements CreatorAppService {

    /** 昵称最大长度 */
    private static final int DISPLAY_NAME_MAX_LENGTH = 64;

    /** 简介最大长度 */
    private static final int BIO_MAX_LENGTH = 500;

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 停用状态 */
    private static final int STATUS_DISABLED = 0;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 创作者头像代理地址前缀 */
    private static final String AVATAR_PATH_PREFIX = "/video/v1/creator/";

    /** 创作者头像代理地址后缀 */
    private static final String AVATAR_PATH_SUFFIX = "/avatar";

    private final CreatorRepository creatorRepository;

    private final FileAssetPort fileAssetPort;

    private final VideoProperties videoProperties;

    @Override
    public PageResult<CreatorAdminView> adminPage(CreatorQueryCommand command) {
        CreatorQuery query = new CreatorQuery();
        query.setKeyword(command.getKeyword());
        query.setStatus(command.getStatus());
        long current = Math.max(command.getCurrent(), 1L);
        long size = clampSize(command.getSize());
        query.setCurrent(current);
        query.setSize(size);
        List<CreatorAdminView> records = creatorRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, creatorRepository.count(query), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorAdminView update(Long id, CreatorUpdateCommand command) {
        Creator creator = creatorRepository.findById(id)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CREATOR_NOT_FOUND));
        applyDisplayName(creator, command.getDisplayName());
        applyBio(creator, command.getBio());
        if (command.getStatus() != null) {
            creator.setStatus(parseStatus(command.getStatus()));
        }
        if (command.getAvatarFileId() != null) {
            creator.setAvatarFileId(command.getAvatarFileId() <= 0
                    ? null : validateAvatar(command.getAvatarFileId()));
        }
        creatorRepository.update(creator);
        return toView(creator);
    }

    /**
     * 写入并校验创作者昵称。
     *
     * @param creator 创作者
     * @param displayName 昵称，空白表示不修改
     */
    private void applyDisplayName(Creator creator, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return;
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "创作者昵称不能超过 64 个字符");
        }
        creator.setDisplayName(trimmed);
    }

    /**
     * 写入并校验创作者简介。
     *
     * @param creator 创作者
     * @param bio 简介，{@code null} 表示不修改
     */
    private void applyBio(Creator creator, String bio) {
        if (bio == null) {
            return;
        }
        String trimmed = bio.trim();
        if (trimmed.length() > BIO_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "创作者简介不能超过 500 个字符");
        }
        creator.setBio(trimmed.isEmpty() ? null : trimmed);
    }

    /**
     * 校验创作者状态取值。
     *
     * @param status 请求状态
     * @return 归一化状态
     */
    private int parseStatus(Integer status) {
        int value = status;
        if (value != STATUS_ENABLED && value != STATUS_DISABLED) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "创作者状态仅支持 0 或 1");
        }
        return value;
    }

    /**
     * 校验头像图片（MIME 白名单）并返回文件 ID。
     *
     * @param fileId 头像文件 ID
     * @return 校验通过的文件 ID
     */
    private Long validateAvatar(Long fileId) {
        Optional<FileAsset> asset = fileAssetPort.find(fileId, TenantContext.requireId());
        FileAsset image = asset.orElseThrow(
                () -> VideoErrorCode.exception(VideoErrorCode.UPLOAD_FILE_INVALID));
        VideoUploadPolicy.validateImage(image, imageMimeTypes());
        return fileId;
    }

    /**
     * 图片 MIME 白名单（小写）。
     *
     * @return MIME 集合
     */
    private Set<String> imageMimeTypes() {
        List<String> formats = videoProperties.getImageFormats();
        return formats == null ? Collections.emptySet()
                : formats.stream().map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /**
     * 创作者转运营视图。
     *
     * @param creator 创作者
     * @return 运营视图
     */
    private CreatorAdminView toView(Creator creator) {
        CreatorAdminView view = new CreatorAdminView();
        view.setId(creator.getId());
        view.setUserId(creator.getUserId());
        view.setDisplayName(creator.getDisplayName());
        view.setBio(creator.getBio());
        view.setStatus(creator.getStatus());
        view.setActive(creator.active());
        view.setAvatarUrl(creator.getAvatarFileId() == null || creator.getAvatarFileId() <= 0
                ? null : AVATAR_PATH_PREFIX + creator.getId() + AVATAR_PATH_SUFFIX);
        return view;
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
