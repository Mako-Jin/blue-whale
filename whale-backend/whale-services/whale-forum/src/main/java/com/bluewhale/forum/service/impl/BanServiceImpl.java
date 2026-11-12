package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumUserBanMapper;
import com.bluewhale.forum.service.BanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 禁言/封号实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class BanServiceImpl implements BanService {

    /** 提示里的时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 禁言 */
    public static final String TYPE_MUTE = "MUTE";

    /** 封号 */
    public static final String TYPE_BAN = "BAN";

    private final ForumUserBanMapper banMapper;

    @Override
    public ForumUserBan activeBan(Long userId) {
        if (userId == null) {
            return null;
        }
        return banMapper.selectActive(TenantContext.requireId(), userId, LocalDateTime.now());
    }

    @Override
    public void assertNotMuted(Long userId) {
        ForumUserBan ban = activeBan(userId);
        if (ban == null) {
            return;
        }
        String until = ban.getExpireTime() == null ? "永久" : ban.getExpireTime().format(TIME_FORMAT);
        String what = TYPE_BAN.equals(ban.getBanType()) ? "账号已被封禁" : "禁言中";
        throw new BizException(ForumErrorCodes.USER_MUTED,
                what + "（至 " + until + "），原因：" + ban.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForumUserBan ban(Long userId, String banType, int days, String reason,
                            Long operatorId, Long reportId) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "被处置用户不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "处置原因必填（通知与申诉依据）");
        }
        // 先解除旧的：同一用户同时存在多条生效记录会让「到期时间」失去唯一解释
        banMapper.releaseAll(TenantContext.requireId(), userId, operatorId);
        ForumUserBan ban = new ForumUserBan();
        ban.setTenantId(TenantContext.requireId());
        ban.setUserId(userId);
        ban.setBanType(banType == null ? TYPE_MUTE : banType);
        ban.setReason(reason);
        ban.setExpireTime(days <= 0 ? null : LocalDateTime.now().plusDays(days));
        ban.setStatus("ACTIVE");
        ban.setOperatorId(operatorId);
        ban.setSourceReportId(reportId);
        banMapper.insert(ban);
        return ban;
    }

    @Override
    public int release(Long userId, Long operatorId) {
        return banMapper.releaseAll(TenantContext.requireId(), userId, operatorId);
    }
}
