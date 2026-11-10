package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.doc.application.service.DocShareService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageShare;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageShareMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.interfaces.dto.DocPageShareCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageShareVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 页面登录内分享应用服务实现。
 *
 * <p>分享链接不做匿名公开：访问者必须已登录，令牌只决定「哪一页可读」，
 * 不会提升页面编辑权限；撤销与过期后立即失效。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocShareServiceImpl implements DocShareService {

    /** 分享访问路径前缀 */
    private static final String SHARE_PATH = "/doc/share/";

    /** 令牌字节数（Base64URL 编码后 43 字符） */
    private static final int TOKEN_BYTES = 32;

    /** 有效状态 */
    private static final int STATUS_ACTIVE = 1;

    /** 已撤销状态 */
    private static final int STATUS_REVOKED = 0;

    private final DocPageShareMapper shareMapper;

    private final DocPageMapper pageMapper;

    private final DocSpaceMapper spaceMapper;

    private final DocAccessGuard accessGuard;

    private final DocProperties docProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageShareVO create(Long pageId, DocPageShareCreateRequest request, Long userId) {
        DocPage page = requireManageablePage(pageId, userId);
        int days = resolveExpireDays(request);
        DocPageShare share = new DocPageShare();
        share.setPageId(page.getId());
        share.setShareToken(newToken());
        share.setShareRole(DocMemberRole.READER.name());
        share.setExpiresAt(days <= 0 ? null : LocalDateTime.now().plusDays(days));
        share.setStatus(STATUS_ACTIVE);
        share.setCreatedBy(userId);
        shareMapper.insert(share);
        log.info("页面分享已创建: pageId={}, shareId={}, expireDays={}, operator={}",
                pageId, share.getId(), days, userId);
        return toVO(share);
    }

    @Override
    public List<DocPageShareVO> list(Long pageId, Long userId) {
        requireManageablePage(pageId, userId);
        return shareMapper.selectList(Wrappers.<DocPageShare>lambdaQuery()
                        .eq(DocPageShare::getPageId, pageId)
                        .orderByDesc(DocPageShare::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long pageId, Long shareId, Long userId) {
        DocPage page = requireManageablePage(pageId, userId);
        DocPageShare share = shareMapper.selectById(shareId);
        if (share == null || !page.getId().equals(share.getPageId())) {
            throw DocErrorCode.exception(DocErrorCode.SHARE_INVALID);
        }
        share.setStatus(STATUS_REVOKED);
        share.setShareToken("revoked-" + share.getId());
        shareMapper.updateById(share);
        log.info("页面分享已撤销: pageId={}, shareId={}, operator={}", pageId, shareId, userId);
    }

    @Override
    public DocPageVO resolve(String token, Long userId) {
        if (token == null || token.isBlank()) {
            throw DocErrorCode.exception(DocErrorCode.SHARE_INVALID);
        }
        DocPageShare share = shareMapper.selectOne(Wrappers.<DocPageShare>lambdaQuery()
                .eq(DocPageShare::getShareToken, token.trim())
                .eq(DocPageShare::getStatus, STATUS_ACTIVE));
        if (share == null || isExpired(share)) {
            throw DocErrorCode.exception(DocErrorCode.SHARE_INVALID);
        }
        DocPage page = pageMapper.selectById(share.getPageId());
        if (page == null || !accessGuard.canRead(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.SHARE_INVALID);
        }
        return toSharedVO(page, share);
    }

    /**
     * 读取页面并校验分享管理权限（EDITOR 及以上）。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 页面
     */
    private DocPage requireManageablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePageRead(pageId, userId);
        accessGuard.requireMember(page.getSpaceId(), userId, DocMemberRole.EDITOR);
        return page;
    }

    /**
     * 解析有效期天数。
     *
     * @param request 创建请求
     * @return 天数；0 表示不限期
     */
    private int resolveExpireDays(DocPageShareCreateRequest request) {
        int requested = request == null || request.getExpireDays() == null
                ? docProperties.getShareDefaultExpireDays() : request.getExpireDays();
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, docProperties.getShareMaxExpireDays());
    }

    /**
     * 生成不可猜分享令牌。
     *
     * @return Base64URL 令牌
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 判断分享是否过期。
     *
     * @param share 分享记录
     * @return 已过期返回 {@code true}
     */
    private boolean isExpired(DocPageShare share) {
        return share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now());
    }

    /**
     * 分享实体转视图。
     *
     * @param share 分享记录
     * @return 视图
     */
    private DocPageShareVO toVO(DocPageShare share) {
        DocPageShareVO vo = new DocPageShareVO();
        vo.setId(share.getId());
        vo.setPageId(share.getPageId());
        vo.setShareToken(share.getShareToken());
        vo.setSharePath(SHARE_PATH + share.getShareToken());
        vo.setShareRole(share.getShareRole());
        vo.setExpiresAt(share.getExpiresAt());
        vo.setStatus(share.getStatus());
        vo.setCreatedBy(share.getCreatedBy());
        vo.setCreateTime(share.getCreateTime());
        vo.setExpired(isExpired(share));
        return vo;
    }

    /**
     * 构建分享页面的只读视图。
     *
     * @param page  页面
     * @param share 分享记录
     * @return 只读视图
     */
    private DocPageVO toSharedVO(DocPage page, DocPageShare share) {
        DocPageVO vo = new DocPageVO();
        vo.setId(page.getId());
        vo.setSpaceId(page.getSpaceId());
        DocSpace space = spaceMapper.selectById(page.getSpaceId());
        vo.setSpaceName(space == null ? null : space.getName());
        vo.setSpaceVisibility(space == null ? null : space.getVisibility());
        vo.setParentId(page.getParentId());
        vo.setTitle(page.getTitle());
        vo.setSlug(page.getSlug());
        vo.setPageType(page.getPageType());
        vo.setContentJson(DocPageType.FOLDER.name().equals(page.getPageType()) ? null : page.getContentJson());
        vo.setStatus(page.getStatus());
        vo.setRevision(page.getRevision());
        vo.setPublishedRevision(page.getPublishedRevision());
        vo.setSortNo(page.getSortNo());
        vo.setIcon(page.getIcon());
        vo.setAuthorId(page.getAuthorId());
        vo.setLastEditorId(page.getLastEditorId());
        vo.setPublishedAt(page.getPublishedAt());
        vo.setArchivedAt(page.getArchivedAt());
        vo.setCreateTime(page.getCreateTime());
        vo.setUpdateTime(page.getUpdateTime());
        vo.setRestricted(accessGuard.isRestricted(page));
        vo.setCanEdit(false);
        vo.setCanComment(false);
        vo.setCanManage(false);
        vo.setStarred(false);
        vo.setBreadcrumb(List.of());
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该页面已归档，暂不可通过分享访问");
        }
        return vo;
    }
}
