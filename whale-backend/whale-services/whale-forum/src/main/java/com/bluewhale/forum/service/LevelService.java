package com.bluewhale.forum.service;

import com.bluewhale.forum.dto.LevelVO;

/**
 * 鲸阶计算（F-07-03）。
 *
 * <p>等级由 append-only 经验账本的累计值推导，因此天然只升不降；
 * 阈值与名称来自 {@code whale.forum.level-thresholds} / {@code level-names}。</p>
 *
 * @author BlueWhale
 */
public interface LevelService {

    /**
     * 按经验总额计算鲸阶。
     *
     * @param totalExp 经验总额
     * @return 等级描述
     */
    LevelVO describe(long totalExp);
}
