package com.bluewhale.video.application.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 电影授权到期自动下线任务：每日扫描授权已到期且仍处于已发布状态的电影并下架。
 *
 * <p>后台任务无租户上下文，租户插件不加条件，可覆盖全部租户；下架后内容聚合的
 * {@code copyrightExpireAt} 已同步过期，播放票据与流式读取会被直接阻断，
 * 可播缓存（30 秒）与首页缓存（≤70 秒）自然过期，无需按租户主动失效。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MovieLicenseExpiryScheduler {

    /** 单批扫描上限 */
    private static final int BATCH_LIMIT = 200;

    private final MovieRepository movieRepository;

    private final ContentRepository contentRepository;

    /**
     * 每日 03:10 下线授权已到期的电影（单个失败不影响其余内容）。
     *
     * @return 执行摘要（扫描数 / 下线数 / 失败数）
     */
    @ScheduledJob(code = "video.license-expiry.offline", cron = "0 10 3 * * ?", timeoutSeconds = 1800)
    public String offlineExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> contentIds = movieRepository.listExpiredLicenseContentIds(now, BATCH_LIMIT);
        int offline = 0;
        int failed = 0;
        for (Long contentId : contentIds) {
            try {
                if (offlineOne(contentId)) {
                    offline++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("电影授权到期自动下线失败: contentId={}, error={}", contentId, ex.getMessage());
            }
        }
        log.info("电影授权到期自动下线完成: scanned={}, offline={}, failed={}",
                contentIds.size(), offline, failed);
        return "scanned=" + contentIds.size() + ", offline=" + offline + ", failed=" + failed;
    }

    /**
     * 下线单部授权到期电影（已非发布状态时跳过）。
     *
     * @param contentId 内容 ID
     * @return 实际下线返回 {@code true}
     */
    private boolean offlineOne(Long contentId) {
        Optional<Content> found = contentRepository.findById(contentId);
        if (found.isEmpty() || found.get().getPublishState() != PublishState.PUBLISHED) {
            return false;
        }
        Content content = found.get();
        content.offline();
        contentRepository.update(content);
        return true;
    }
}
