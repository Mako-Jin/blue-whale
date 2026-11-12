package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumThreadView;
import com.bluewhale.forum.mapper.ForumThreadViewMapper;
import com.bluewhale.forum.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 浏览去重实现。
 *
 * <p>三步走：</p>
 * <ol>
 *   <li>条件更新 {@code last_view_time}——只有「窗口已过」的行才会被更新，返回 1 表示该计；</li>
 *   <li>返回 0 时尝试插入新行——行不存在才会插入成功，插入撞唯一键说明窗口内已计过；</li>
 *   <li>去重基建本身异常时按「未去重」处理——宁可多计一次浏览，也不能让详情页 500。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private final ForumThreadViewMapper viewMapper;

    private final ForumProperties properties;

    @Override
    public boolean touch(Long threadId, Long userId) {
        if (threadId == null || userId == null) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.getViewDedupeHours());
        try {
            if (viewMapper.touchIfExpired(threadId, userId, cutoff) > 0) {
                return true;
            }
            ForumThreadView view = new ForumThreadView();
            view.setTenantId(TenantContext.requireId());
            view.setThreadId(threadId);
            view.setUserId(userId);
            view.setLastViewTime(LocalDateTime.now());
            viewMapper.insert(view);
            return true;
        } catch (DuplicateKeyException e) {
            // 窗口内已计过：唯一键就是判据，不是错误
            return false;
        } catch (RuntimeException e) {
            log.warn("浏览去重失败，按未去重处理 threadId={} userId={}", threadId, userId, e);
            return true;
        }
    }
}
