package com.bluewhale.forum.service.impl;

import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.LevelVO;
import com.bluewhale.forum.service.LevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 鲸阶计算实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class LevelServiceImpl implements LevelService {

    private final ForumProperties properties;

    @Override
    public LevelVO describe(long totalExp) {
        List<Long> thresholds = properties.getLevelThresholds();
        List<String> names = properties.getLevelNames();
        boolean hasThresholds = thresholds != null && !thresholds.isEmpty();
        int index = 0;
        if (hasThresholds) {
            for (int i = 0; i < thresholds.size(); i++) {
                if (totalExp >= thresholds.get(i)) {
                    index = i;
                }
            }
        }
        LevelVO vo = new LevelVO();
        vo.setLevel(index + 1);
        vo.setName(names != null && index < names.size() ? names.get(index) : "Lv" + (index + 1));
        vo.setMinExp(hasThresholds ? thresholds.get(index) : 0L);
        vo.setNextExp(hasThresholds && index + 1 < thresholds.size() ? thresholds.get(index + 1) : null);
        return vo;
    }
}
