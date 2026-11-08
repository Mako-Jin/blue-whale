package com.bluewhale.party.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知扫描定时任务（M-P4）。
 *
 * <p>手工扫描与定时扫描走同一个应用服务，幂等判定不依赖触发方式。
 * 由 whale-job 调度中心触发，返回值是执行摘要，直接落 {@code job_log.result}。</p>
 *
 * <p>整轮异常不再吞掉：原先 {@code catch} 后只记 warn，通知停摆会被调度侧
 * 长期记成成功。党务与工会两段扫描保持先后顺序（与手写入口一致），
 * 前一段失败时后一段本轮不执行，由调度中心记失败并暴露原因。</p>
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
     *
     * @return 执行摘要（{@code party/union} 发送条数）
     */
    @ScheduledJob(code = "party.notice.scan",
            cron = "${whale.party.notice-cron:0 0 9 * * *}",
            timeoutSeconds = 1800)
    public String scan() {
        NoticeScanResultView party = notifyAppService.scanParty();
        log.info("党务提醒扫描完成: total={}, detail={}", party.getTotalSent(), party.getSentByScene());
        NoticeScanResultView union = notifyAppService.scanUnion();
        log.info("工会通知扫描完成: total={}", union.getTotalSent());
        return "party=" + party.getTotalSent() + ", union=" + union.getTotalSent();
    }
}
