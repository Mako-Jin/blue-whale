package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.dto.AuthorStatVO;
import com.bluewhale.forum.dto.LevelVO;
import com.bluewhale.forum.dto.ProfileVO;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.LevelService;
import com.bluewhale.forum.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 个人主页实现。
 *
 * <p>统计走单次聚合查询而不是列表页口径复用：主页是高频读，
 * 「发帖 / 获花 / 精华」三项各打一次数据库没有意义。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ForumThreadMapper threadMapper;

    private final ExpService expService;

    private final LevelService levelService;

    private final BanService banService;

    @Override
    public ProfileVO profile(Long userId) {
        Long tenantId = TenantContext.requireId();
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.profile.user-id-required", "用户ID不能为空");
        }
        AuthorStatVO stat = threadMapper.selectAuthorStats(tenantId, userId);
        long expTotal = expService.totalOf(userId);
        LevelVO level = levelService.describe(expTotal);
        ProfileVO vo = new ProfileVO();
        vo.setUserId(userId);
        vo.setLevel(level.getLevel());
        vo.setLevelName(level.getName());
        vo.setNextLevelExp(level.getNextExp());
        vo.setExpTotal(expTotal);
        vo.setThreadCount(stat == null || stat.getThreadCount() == null ? 0L : stat.getThreadCount());
        vo.setLikeCount(stat == null || stat.getLikeCount() == null ? 0L : stat.getLikeCount());
        vo.setEssenceCount(stat == null || stat.getEssenceCount() == null ? 0L : stat.getEssenceCount());
        ForumUserBan ban = banService.activeBan(userId);
        vo.setBanned(ban != null);
        if (ban != null) {
            vo.setBanType(ban.getBanType());
            // 只回类型与到期时间：处置原因是治理信息，不该挂在对方主页
            vo.setBanUntil(ban.getExpireTime());
        }
        return vo;
    }
}
