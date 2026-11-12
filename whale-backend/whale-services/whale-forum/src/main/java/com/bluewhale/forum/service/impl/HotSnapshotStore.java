package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.forum.dto.HotCandidate;
import com.bluewhale.forum.entity.ForumHotSnapshot;
import com.bluewhale.forum.mapper.ForumHotSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 热榜快照写入器：独立成 Bean 是为了让「先清空再整表写入」真正落在一个事务里。
 *
 * <p>放在 {@code HotServiceImpl} 内部自调用会绕过事务代理——注解静默失效，
 * 清空成功而写入失败时快照就空了，热榜要等下一轮（30 分钟）才恢复。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class HotSnapshotStore {

    private final ForumHotSnapshotMapper snapshotMapper;

    /**
     * 用新一轮候选整体覆盖租户某一档的快照。
     *
     * <p>只清当前窗口的行：24h 与 7d 两档并存，整租户清空会把另一档一并抹掉。</p>
     *
     * @param tenantId 租户ID
     * @param hotWindow 窗口（24h / 7d）
     * @param candidates 已按热度分倒序的候选
     * @param snapshotTime 快照时间
     * @return 写入条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int replace(Long tenantId, String hotWindow, List<HotCandidate> candidates,
                       LocalDateTime snapshotTime) {
        snapshotMapper.purgeByTenantAndWindow(tenantId, hotWindow);
        int rank = 1;
        for (HotCandidate candidate : candidates) {
            ForumHotSnapshot snapshot = new ForumHotSnapshot();
            snapshot.setId(IdWorker.getId());
            snapshot.setTenantId(tenantId);
            snapshot.setHotWindow(hotWindow);
            snapshot.setRankNo(rank++);
            snapshot.setThreadId(candidate.getId());
            snapshot.setBoardId(candidate.getBoardId());
            snapshot.setTitle(candidate.getTitle());
            snapshot.setSummary(candidate.getSummary());
            snapshot.setAuthorId(candidate.getAuthorId());
            snapshot.setReplyCount(candidate.getReplyCount() == null ? 0 : candidate.getReplyCount());
            snapshot.setLikeCount(candidate.getLikeCount() == null ? 0 : candidate.getLikeCount());
            snapshot.setViewCount(candidate.getViewCount() == null ? 0 : candidate.getViewCount());
            snapshot.setScore(candidate.getScore());
            snapshot.setSnapshotTime(snapshotTime);
            snapshot.setCreateTime(snapshotTime);
            snapshotMapper.insert(snapshot);
        }
        return candidates.size();
    }
}
