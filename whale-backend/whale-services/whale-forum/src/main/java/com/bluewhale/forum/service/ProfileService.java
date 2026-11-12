package com.bluewhale.forum.service;

import com.bluewhale.forum.dto.ProfileVO;

/**
 * 个人主页（F-07-01）。
 *
 * @author BlueWhale
 */
public interface ProfileService {

    /**
     * 查询用户主页信息。
     *
     * @param userId 用户ID
     * @return 主页视图
     */
    ProfileVO profile(Long userId);
}
