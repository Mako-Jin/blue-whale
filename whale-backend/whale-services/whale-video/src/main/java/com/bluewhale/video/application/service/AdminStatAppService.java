package com.bluewhale.video.application.service;

import com.bluewhale.video.application.view.StatOverviewView;

/**
 * 视频运营统计应用服务。
 *
 * @author BlueWhale
 */
public interface AdminStatAppService {

    /**
     * 统计概览：内容数量 / 播放次数 / 待审评论 / 授权到期 / 片单与创作者。
     *
     * @param expiringDays 授权到期提醒窗口（天）
     * @return 统计概览
     */
    StatOverviewView overview(int expiringDays);
}
