package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.bluewhale.forum.service.ExpService;
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
 * 热榜服务实现（24h / 7d 双档）。
 *
 * <p>读写分离得很硬：<b>读只读快照，写只在刷新</b>。列表页现算窗口聚合会把
 * 每次打开首页都变成一次全表扫描，而且同一榜单在不同时刻排序不一样，没法解释名次。</p>
 *
 * <p>7d 档第一名发金榜经验（HOT_TOP1，+1000）：经验唯一键按帖子幂等，
 * 30 分钟一轮的刷新不会重复发；榜单易主后新榜首的帖子没发过，自然补上。</p>
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

    private final ExpService expService;

    private final ForumProperties properties;

    @Override
    public List<HotItemVO> list(String window, Integer limit, ForumActor actor) {
        TenantContext.requireId();
        String win = normalizeWindow(window);
        int top = normalizeLimit(limit);
        List<ForumHotSnapshot> rows = snapshotMapper.selectList(new QueryWrapper<ForumHotSnapshot>()
                .eq("hot_window", win)
                .orderByAsc("rank_no")
                .last("LIMIT " + top));
        if (rows.isEmpty()) {
            // 兜底：该档快照还没刷过（首次部署 / 刷新失败）时给实时头部，只读不写
            return liveTop(win, top, actor);
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
        LocalDateTime now = LocalDateTime.now();
        List<HotCandidate> day = candidates(tenantId, WINDOW_24H, properties.getHotTopN());
        List<HotCandidate> week = candidates(tenantId, WINDOW_7D, properties.getHotTopN());
        int written = snapshotStore.replace(tenantId, WINDOW_24H, day, now)
                + snapshotStore.replace(tenantId, WINDOW_7D, week, now);
        awardHotTop1(week);
        log.info("热榜快照刷新完成 tenantId={} 24h={} 7d={}", tenantId, day.size(), week.size());
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
     * 7d 金榜第一名发经验（HOT_TOP1）。
     *
     * <p>只发给 7d 榜：24h 榜波动太快，第一名频繁易主会变成「轮着白送」。
     * 经验唯一键按帖子幂等——同一帖子即使连续霸榜也只发一次。
     * 匿名帖不计（F-07-04 硬规则）。</p>
     *
     * @param week 7d 档候选（已按分数倒序）
     */
    private void awardHotTop1(List<HotCandidate> week) {
        if (week.isEmpty()) {
            return;
        }
        HotCandidate top = week.get(0);
        if (top.getAnonymous() != null && top.getAnonymous() == 1) {
            return;
        }
        expService.award(top.getAuthorId(), ExpService.EVENT_HOT_TOP1,
                properties.getExpHotTop1(), top.getId());
    }

    /**
     * 实时头部（不落库），仅在快照为空时使用。
     *
     * @param window 窗口
     * @param top 条数
     * @param actor 当前操作者
     * @return 热榜条目
     */
    private List<HotItemVO> liveTop(String window, int top, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        Set<Long> visibleBoards = visibleBoardIds(actor);
        List<HotItemVO> items = new ArrayList<>(top);
        int rank = 1;
        for (HotCandidate candidate : candidates(tenantId, window, top)) {
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
     * 取窗口内候选（已按热度分倒序）。24h 档启用小时衰减，7d 档不衰减。
     *
     * @param tenantId 租户ID
     * @param window 窗口
     * @param limit 条数
     * @return 候选
     */
    private List<HotCandidate> candidates(Long tenantId, String window, int limit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = WINDOW_24H.equals(window)
                ? now.minusHours(properties.getHotWindowHours24())
                : now.minusDays(properties.getHotWindowDays());
        int decayHours = WINDOW_24H.equals(window) ? properties.getHotDecayHalfLifeHours() : 0;
        return threadMapper.selectHotCandidates(tenantId, from,
                properties.getHotReplyWeight(), properties.getHotLikeWeight(),
                properties.getHotViewWeight(), properties.getHotEssenceBonus(),
                properties.getHotPenaltyWeight(), decayHours, limit);
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
     * 归一化窗口（非法值按 7d，保持旧行为兼容）。
     *
     * @param window 请求窗口
     * @return 生效窗口
     */
    private String normalizeWindow(String window) {
        return WINDOW_24H.equalsIgnoreCase(window) ? WINDOW_24H : WINDOW_7D;
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
