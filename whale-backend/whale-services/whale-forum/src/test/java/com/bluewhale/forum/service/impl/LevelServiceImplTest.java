package com.bluewhale.forum.service.impl;

import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.LevelVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鲸阶计算单测：阈值边界与满级。
 *
 * @author BlueWhale
 */
class LevelServiceImplTest {

    private final LevelServiceImpl levelService = new LevelServiceImpl(new ForumProperties());

    @Test
    @DisplayName("鲸阶：0 经验为 1 级，阈值恰好命中升到下一级")
    void describeBoundaries() {
        LevelVO zero = levelService.describe(0);
        assertThat(zero.getLevel()).isEqualTo(1);
        assertThat(zero.getName()).isEqualTo("初入鲸门");
        assertThat(zero.getNextExp()).isEqualTo(100L);

        LevelVO exact = levelService.describe(100);
        assertThat(exact.getLevel()).isEqualTo(2);
        assertThat(exact.getName()).isEqualTo("观澜");
        assertThat(exact.getMinExp()).isEqualTo(100L);
        assertThat(exact.getNextExp()).isEqualTo(500L);
    }

    @Test
    @DisplayName("鲸阶：差 1 点不升级")
    void describeJustBelowThreshold() {
        assertThat(levelService.describe(99).getLevel()).isEqualTo(1);
        assertThat(levelService.describe(499).getLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("鲸阶：满级后 nextExp 为 null，负经验兜底 1 级")
    void describeCapAndNegative() {
        LevelVO top = levelService.describe(10000);
        assertThat(top.getLevel()).isEqualTo(6);
        assertThat(top.getName()).isEqualTo("鲸首");
        assertThat(top.getNextExp()).isNull();

        assertThat(levelService.describe(-1).getLevel()).isEqualTo(1);
    }
}
