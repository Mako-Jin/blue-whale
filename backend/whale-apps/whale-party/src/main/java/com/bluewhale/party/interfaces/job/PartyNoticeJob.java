package com.bluewhale.party.interfaces.job;

import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知扫描定时任务（M-P4）。
 *
 * <p>手工扫描与定时扫描走同一个应用服务，幂等判定不依赖触发方式；
 * 任务本身吞掉异常，避免一次下游抖动让调度线程停摆。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyNoticeJob {

    private final NotifyAppService notifyAppService;

    /**
     * 每日扫描党务提醒与工会活动通知。
     */
    @Scheduled(cron = "${whale.party.notice-cron:0 0 9 * * *}")
    public void scan() {
        try {
            NoticeScanResultView party = notifyAppService.scanParty();
            log.info("党务提醒扫描完成: total={}, detail={}", party.getTotalSent(), party.getSentByScene());
            NoticeScanResultView union = notifyAppService.scanUnion();
            log.info("工会通知扫描完成: total={}", union.getTotalSent());
        } catch (RuntimeException exception) {
            log.warn("通知扫描任务失败: error={}", exception.getMessage());
        }
    }
}
