package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocStarService;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageStar;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageStarMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.interfaces.vo.DocStarItemVO;
import com.bluewhale.doc.interfaces.vo.DocStarToggleVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 页面收藏应用服务实现。
 *
 * <p>取消收藏为逻辑删除，重新收藏复用原行；收藏列表过滤掉用户已无权访问或已删除的页面。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocStarServiceImpl implements DocStarService {

    /** 逻辑删除标记：已删除 */
    private static final int DELETED_YES = 1;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final DocPageStarMapper starMapper;

    private final DocPageMapper pageMapper;

    private final DocSpaceMapper spaceMapper;

    private final DocAccessGuard accessGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocStarToggleVO toggle(Long pageId, Long userId) {
        accessGuard.requirePageRead(pageId, userId);
        Long tenantId = TenantContext.requireId();
        DocPageStar star = starMapper.selectAny(tenantId, userId, pageId);
        boolean starred;
        if (star == null) {
            DocPageStar created = new DocPageStar();
            created.setUserId(userId);
            created.setPageId(pageId);
            starMapper.insert(created);
            starred = true;
        } else if (star.getDeleted() != null && star.getDeleted() == DELETED_YES) {
            starMapper.reactivate(tenantId, star.getId());
            starred = true;
        } else {
            starMapper.deactivate(tenantId, star.getId());
            starred = false;
        }
        log.info("页面收藏状态切换: pageId={}, userId={}, starred={}", pageId, userId, starred);
        DocStarToggleVO vo = new DocStarToggleVO();
        vo.setStarred(starred);
        return vo;
    }

    @Override
    public PageResult<DocStarItemVO> page(long current, long size, Long userId) {
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<DocPageStar> starPage = starMapper.selectPage(new Page<>(Math.max(current, 1L), pageSize),
                Wrappers.<DocPageStar>lambdaQuery()
                        .eq(DocPageStar::getUserId, userId)
                        .orderByDesc(DocPageStar::getCreateTime));
        List<DocStarItemVO> records = resolveItems(starPage.getRecords(), userId);
        return PageResult.of(records, starPage.getTotal(), starPage.getCurrent(), starPage.getSize());
    }

    /**
     * 将收藏行解析为可访问的页面条目。
     *
     * @param stars  收藏行
     * @param userId 用户ID
     * @return 收藏条目
     */
    private List<DocStarItemVO> resolveItems(List<DocPageStar> stars, Long userId) {
        if (stars.isEmpty()) {
            return List.of();
        }
        List<Long> pageIds = stars.stream().map(DocPageStar::getPageId).toList();
        Map<Long, DocPage> pages = pageMapper.selectBatchIds(pageIds).stream()
                .collect(Collectors.toMap(DocPage::getId, page -> page));
        if (pages.isEmpty()) {
            return List.of();
        }
        List<Long> spaceIds = pages.values().stream().map(DocPage::getSpaceId).distinct().toList();
        Map<Long, DocSpace> spaces = spaceMapper.selectBatchIds(spaceIds).stream()
                .collect(Collectors.toMap(DocSpace::getId, space -> space));
        List<DocStarItemVO> items = new ArrayList<>();
        for (DocPageStar star : stars) {
            DocPage page = pages.get(star.getPageId());
            DocSpace space = page == null ? null : spaces.get(page.getSpaceId());
            if (page == null || space == null || !accessGuard.canRead(page, userId)) {
                continue;
            }
            items.add(toVO(star, page, space));
        }
        return items;
    }

    /**
     * 收藏行转视图。
     *
     * @param star  收藏行
     * @param page  页面
     * @param space 空间
     * @return 收藏条目
     */
    private DocStarItemVO toVO(DocPageStar star, DocPage page, DocSpace space) {
        DocStarItemVO vo = new DocStarItemVO();
        vo.setPageId(page.getId());
        vo.setSpaceId(page.getSpaceId());
        vo.setSpaceName(space.getName());
        vo.setTitle(page.getTitle());
        vo.setSlug(page.getSlug());
        vo.setStatus(page.getStatus());
        vo.setStarredAt(star.getCreateTime());
        vo.setUpdateTime(page.getUpdateTime());
        return vo;
    }
}
