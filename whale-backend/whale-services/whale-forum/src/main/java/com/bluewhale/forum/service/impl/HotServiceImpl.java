package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.dto.HotCandidate;
import com.bluewhale.forum.dto.HotItemVO;
import com.bluewhale.forum.entity.ForumHotSnapshot;
import com.bluewhale.forum.mapper.ForumHotSnapshotMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.HotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 热榜服务实现。
 *
 * <p>读写分离得很硬：<b>读只读快照，写只在刷新</b>。列表页现算 7 天窗口聚合会把
 * 每次打开首页都变成一次全表扫描，而且同一榜单在不同时刻排序不一样，没法解释名次。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotServiceImpl implements HotService {

    private final ForumThreadMapper threadMapper;

    private final ForumHotSnapshotMapper snapshotMapper;

    private final HotSnapshotStore snapshotStore;

    private final BoardService boardService;

    private final ForumProperties properties;

    @Override
    public List<HotItemVO> list(Integer limit, ForumActor actor) {
        TenantContext.requireId();
        int top = normalizeLimit(limit);
        List<ForumHotSnapshot> rows = snapshotMapper.selectList(new QueryWrapper<ForumHotSnapshot>()
                .orderByAsc("rank_no")
                .last("LIMIT " + top));
        if (rows.isEmpty()) {
            // 兜底：快照还没刷过（首次部署 / 刷新失败）时给实时头部，只读不写
            return liveTop(top, actor);
        }
        Set<Long> visibleBoards = visibleBoardIds(actor);
        List<HotItemVO> items = new ArrayList<>(rows.size());
        for (ForumHotSnapshot row : rows) {
            if (row.getBoardId() != null && !visibleBoards.contains(row.getBoardId())) {
                // 快照是租户级的，但角色可见版块不该经热榜泄露标题
                continue;
            }
            items.add(toVO(row));
        }
        return items;
    }

    @Override
    public int refresh() {
        Long tenantId = TenantContext.requireId();
        List<HotCandidate> candidates = candidates(tenantId, properties.getHotTopN());
        int written = snapshotStore.replace(tenantId, candidates, LocalDateTime.now());
        log.info("热榜快照刷新完成 tenantId={} count={}", tenantId, written);
        return written;
    }

    @Override
    public int refreshAllTenants() {
        List<Long> tenantIds = threadMapper.selectActiveTenantIds();
        int total = 0;
        for (Long tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                total += refresh();
            } catch (RuntimeException e) {
                // 单租户失败不拖累其他租户，下一轮覆盖自愈
                log.warn("热榜快照刷新失败 tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        return total;
    }

    /**
     * 实时头部（不落库），仅在快照为空时使用。
     *
     * @param top 条数
     * @param actor 当前操作者
     * @return 热榜条目
     */
    private List<HotItemVO> liveTop(int top, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        Set<Long> visibleBoards = visibleBoardIds(actor);
        List<HotItemVO> items = new ArrayList<>(top);
        int rank = 1;
        for (HotCandidate candidate : candidates(tenantId, top)) {
            if (candidate.getBoardId() != null && !visibleBoards.contains(candidate.getBoardId())) {
                continue;
            }
            HotItemVO vo = new HotItemVO();
            vo.setRankNo(rank++);
            vo.setThreadId(candidate.getId());
            vo.setBoardId(candidate.getBoardId());
            vo.setTitle(candidate.getTitle());
            vo.setSummary(candidate.getSummary());
            vo.setAuthorId(candidate.getAuthorId());
            vo.setReplyCount(candidate.getReplyCount());
            vo.setLikeCount(candidate.getLikeCount());
            vo.setViewCount(candidate.getViewCount());
            vo.setScore(candidate.getScore());
            items.add(vo);
        }
        return items;
    }

    /**
     * 取窗口内候选（已按热度分倒序）。
     *
     * @param tenantId 租户ID
     * @param limit 条数
     * @return 候选
     */
    private List<HotCandidate> candidates(Long tenantId, int limit) {
        LocalDateTime from = LocalDateTime.now().minusDays(properties.getHotWindowDays());
        return threadMapper.selectHotCandidates(tenantId, from,
                properties.getHotReplyWeight(), properties.getHotLikeWeight(),
                properties.getHotViewWeight(), properties.getHotPenaltyWeight(), limit);
    }

    /**
     * 当前用户可见版块ID集合。
     *
     * @param actor 当前操作者
     * @return 版块ID集合
     */
    private Set<Long> visibleBoardIds(ForumActor actor) {
        Set<Long> ids = new HashSet<>();
        for (BoardVO board : boardService.listVisible(actor)) {
            if (board.getId() != null) {
                ids.add(board.getId());
            }
        }
        return ids;
    }

    /**
     * 归一化条数（不传取配置上限，越界取上限）。
     *
     * @param limit 请求条数
     * @return 生效条数
     */
    private int normalizeLimit(Integer limit) {
        int max = properties.getHotTopN();
        if (limit == null || limit < 1) {
            return max;
        }
        return Math.min(limit, max);
    }

    /**
     * 快照行转视图。
     *
     * @param row 快照行
     * @return 热榜条目
     */
    private HotItemVO toVO(ForumHotSnapshot row) {
        HotItemVO vo = new HotItemVO();
        vo.setRankNo(row.getRankNo());
        vo.setThreadId(row.getThreadId());
        vo.setBoardId(row.getBoardId());
        vo.setTitle(row.getTitle());
        vo.setSummary(row.getSummary());
        vo.setAuthorId(row.getAuthorId());
        vo.setReplyCount(row.getReplyCount());
        vo.setLikeCount(row.getLikeCount());
        vo.setViewCount(row.getViewCount());
        vo.setScore(row.getScore());
        vo.setSnapshotTime(row.getSnapshotTime());
        return vo;
    }
}
