package com.bluewhale.auth.application.user.password;

import com.bluewhale.auth.application.user.port.PasswordHasher;
import com.bluewhale.auth.domain.user.repository.PasswordHistoryRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 口令策略服务（REQ-SEC-021 / REQ-SEC-023）：统一强度校验、历史复用校验、加密与历史记录。
 *
 * <p>四条口令入口（创建用户 / 更新用户 / 自助改密 / 管理员重置）都经由本服务，
 * 避免策略在接口层分散实现导致口径漂移。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    /** 口令策略配置 */
    private final PasswordPolicyProperties properties;

    /** 口令加密端口（比对历史密文需要 matches） */
    private final PasswordHasher passwordHasher;

    /** 口令历史仓储端口 */
    private final PasswordHistoryRepository passwordHistoryRepository;

    /**
     * 校验明文口令强度
     *
     * @param rawPassword 明文口令
     * @param username 用户名（口令不得包含）
     */
    public void validate(String rawPassword, String username) {
        PasswordPolicy.from(properties).validate(rawPassword, username);
    }

    /**
     * 校验口令未被近期使用（当前口令 + 最近 N 条历史）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param currentEncodedPassword 当前口令密文
     * @param rawPassword 待校验的新口令明文
     */
    public void ensureNotReused(Long tenantId, Long userId, String currentEncodedPassword, String rawPassword) {
        if (currentEncodedPassword != null && passwordHasher.matches(rawPassword, currentEncodedPassword)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "新口令不得与当前口令相同");
        }
        int historySize = Math.max(properties.getHistorySize(), 0);
        if (historySize == 0) {
            return;
        }
        List<String> recent = passwordHistoryRepository.listRecent(tenantId, userId, historySize);
        if (recent.stream().anyMatch(encoded -> passwordHasher.matches(rawPassword, encoded))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "新口令与最近 " + historySize + " 次使用过的口令重复，请更换");
        }
    }

    /**
     * 加密明文口令（调用前应先通过 {@link #validate}）
     *
     * @param rawPassword 明文口令
     * @return BCrypt 密文
     */
    public String encode(String rawPassword) {
        return passwordHasher.encode(rawPassword);
    }

    /**
     * 记录口令历史并按策略裁剪
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param encodedPassword 新口令密文
     */
    public void recordHistory(Long tenantId, Long userId, String encodedPassword) {
        passwordHistoryRepository.append(tenantId, userId, encodedPassword);
        passwordHistoryRepository.trim(tenantId, userId, Math.max(properties.getHistorySize(), 0));
    }

    /**
     * 是否要求新建/重置口令后强制目标用户下次登录改密
     *
     * @return 需要强制改密返回 true
     */
    public boolean isForceChangeOnCreate() {
        return properties.isForceChangeOnCreate();
    }

    /**
     * 口令有效期（天），供登录到期判定
     *
     * @return 有效期天数；小于等于 0 表示不强制到期
     */
    public int maxAgeDays() {
        return properties.getMaxAgeDays();
    }

    /**
     * 口令最小长度（供临时口令生成器保证系统生成的临时口令同样满足策略）
     *
     * @return 最小长度
     */
    public int minLength() {
        return properties.getMinLength();
    }

    /**
     * 口令到期前提醒天数
     *
     * @return 提前提醒天数
     */
    public int warnDays() {
        return properties.getWarnDays();
    }
}
