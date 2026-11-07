package com.bluewhale.crm.service.domain.port;

/**
 * 满意度答卷提交限流端口。
 *
 * @author BlueWhale
 */
public interface SurveyRateLimitPort {

    /**
     * 尝试占用一次提交配额。
     *
     * @param clientIp 来源 IP
     * @param ticketId 工单 ID
     * @return 是否允许提交
     */
    boolean tryAcquire(String clientIp, Long ticketId);
}
