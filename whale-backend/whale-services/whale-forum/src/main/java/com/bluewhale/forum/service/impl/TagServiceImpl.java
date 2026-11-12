package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.dto.TagStatVO;
import com.bluewhale.forum.mapper.ForumThreadTagMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签聚合服务实现。
 *
 * <p>聚合必须先用 {@link BoardService#listVisible} 裁出可见版块再下推 SQL：
 * 标签页与帖子列表必须是同一套可见性判定，否则私密版块的标签名与计数会从标签页漏出去。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    /** 聚合条数硬上限（配置更大也按此截断，防止一次拉全量标签） */
    private static final int HARD_LIMIT = 200;

    private final ForumThreadTagMapper threadTagMapper;

    private final BoardService boardService;

    private final ForumProperties properties;

    @Override
    public List<TagStatVO> list(Integer limit, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        List<Long> boardIds = boardService.listVisible(actor).stream()
                .map(BoardVO::getId)
                .toList();
        if (boardIds.isEmpty()) {
            return List.of();
        }
        int effective = limit == null || limit < 1 ? properties.getTagListLimit() : limit;
        return threadTagMapper.selectTagStats(tenantId, boardIds,
                Math.min(effective, HARD_LIMIT));
    }
}
