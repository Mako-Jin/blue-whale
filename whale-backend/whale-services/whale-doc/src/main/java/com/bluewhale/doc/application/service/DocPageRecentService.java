package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageRecent;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageRecentMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocRecentRow;
import com.bluewhale.doc.interfaces.vo.DocRecentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 最近访问应用服务。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageRecentService {

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 10;

    /** 最大返回条数 */
    private static final int MAX_LIMIT = 20;

    private final DocPageRecentMapper recentMapper;

    /**
     * 记录一次页面访问（同一用户/页面复用行）；失败不影响页面读取。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordVisit(Long pageId, Long userId) {
        if (pageId == null || userId == null) {
            return;
        }
        try {
            DocPageRecent existing = recentMapper.selectOne(Wrappers.<DocPageRecent>lambdaQuery()
                    .eq(DocPageRecent::getUserId, userId)
                    .eq(DocPageRecent::getPageId, pageId));
            LocalDateTime now = LocalDateTime.now();
            if (existing != null) {
                existing.setVisitedAt(now);
                existing.setUpdateTime(now);
                recentMapper.updateById(existing);
                return;
            }
            DocPageRecent recent = new DocPageRecent();
            recent.setUserId(userId);
            recent.setPageId(pageId);
            recent.setVisitedAt(now);
            recentMapper.insert(recent);
        } catch (RuntimeException ex) {
            log.debug("记录最近访问失败: pageId={}, userId={}, reason={}", pageId, userId, ex.getMessage());
        }
    }

    /**
     * 查询用户最近访问的可读页面。
     *
     * @param userId 用户ID
     * @param limit  条数，默认 10，最大 20
     * @return 最近访问列表
     */
    public List<DocRecentVO> recent(Long userId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<DocRecentRow> rows = recentMapper.selectRecent(TenantContext.requireId(), userId, size);
        return rows.stream().map(row -> {
            DocRecentVO vo = new DocRecentVO();
            vo.setPageId(row.getPageId());
            vo.setTitle(row.getTitle());
            vo.setSpaceId(row.getSpaceId());
            vo.setSpaceName(row.getSpaceName());
            vo.setSlug(row.getSlug());
            vo.setVisitedAt(row.getVisitedAt());
            vo.setUpdateTime(row.getUpdateTime());
            return vo;
        }).toList();
    }
}
