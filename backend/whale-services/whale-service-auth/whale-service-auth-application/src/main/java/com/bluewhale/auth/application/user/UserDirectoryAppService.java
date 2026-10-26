package com.bluewhale.auth.application.user;

import com.bluewhale.auth.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户目录应用服务：面向服务间调用的「用户存在且启用」批量判定。
 *
 * <p>仅供内网接口 {@code POST /user/internal/exists} 使用，供 IM 等业务服务在创建会话、
 * 添加群成员前校验目标用户是否真实存在，避免把已停用或已删除的用户写入成员表。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryAppService {

    /** User 聚合仓储 */
    private final UserRepository userRepository;

    /**
     * 批量查询启用状态的用户 ID。
     *
     * @param ids 待校验用户 ID（允许重复，内部去重）
     * @return 存在且启用的用户 ID 列表
     */
    public List<Long> findEnabledIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findEnabledIds(new LinkedHashSet<>(ids));
    }
}
