package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活动人群快照锁定不变量测试。
 *
 * @author BlueWhale
 */
@DisplayName("活动人群快照")
class AudienceTest {

    @Test
    @DisplayName("人群快照锁定后不可重复锁定")
    void lockTwiceRejected() {
        CampaignAudience audience = new CampaignAudience();
        audience.lock(LocalDateTime.of(2026, 11, 8, 10, 0));
        assertTrue(audience.getLocked());
        assertThrows(BizException.class, () -> audience.lock(LocalDateTime.of(2026, 11, 8, 11, 0)));
    }

    @Test
    @DisplayName("人群快照锁定后不可再编辑")
    void lockedAudienceNotEditable() {
        CampaignAudience audience = new CampaignAudience();
        audience.lock(LocalDateTime.now());
        assertThrows(BizException.class, audience::ensureEditable);
    }
}
