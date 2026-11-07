package com.bluewhale.crm.service.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SatisfactionSurvey} 领域单元测试。
 *
 * @author BlueWhale
 */
class SatisfactionSurveyTest {

    @Test
    @DisplayName("签发：置为待答卷并写入有效期")
    void shouldIssueSurvey() {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

        survey.issue(7, now);

        assertEquals(SurveyStatus.SENT, survey.getStatus());
        assertEquals(now, survey.getSentAt());
        assertEquals(now.plusDays(7), survey.getExpiresAt());
        assertNull(survey.getUsedAt());
        assertFalse(survey.isExpired(now.plusDays(6)));
        assertTrue(survey.isExpired(now.plusDays(7)));
    }

    @Test
    @DisplayName("答卷与作废：状态与使用时间")
    void shouldMarkUsedAndRevoke() {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

        survey.markUsed(now);
        assertEquals(SurveyStatus.USED, survey.getStatus());
        assertEquals(now, survey.getUsedAt());

        survey.revoke(now.plusHours(1));
        assertEquals(SurveyStatus.REVOKED, survey.getStatus());
    }

    @Test
    @DisplayName("有效期下限：签发天数小于 1 时按 1 天计算")
    void shouldClampExpireDays() {
        SatisfactionSurvey survey = new SatisfactionSurvey();
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

        survey.issue(0, now);

        assertEquals(now.plusDays(1), survey.getExpiresAt());
    }
}
