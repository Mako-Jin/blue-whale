package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.model.vo.UserConfigVO;
import com.bluewhale.auth.application.port.UserConfigRepository;
import com.bluewhale.auth.application.service.UserConfigAppService;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户配置应用服务：用户自助管理自己的配置（无权限码要求，登录即可）。
 *
 * <p>配置按「当前租户」隔离：同一用户在不同租户下的配置相互独立。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserConfigAppServiceImpl implements UserConfigAppService {

    /** 用户配置仓储 */
    private final UserConfigRepository userConfigRepository;

    /**
     * 列出当前用户在指定租户下的配置（可按前缀过滤）
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param prefix   配置键前缀，空表示不过滤
     * @return 配置列表（按 config_key 升序）
     */
    public List<UserConfigVO> list(Long tenantId, Long userId, String prefix) {
        requireUserScope(tenantId, userId);
        ConfigKeyValidator.requireValidPrefix(prefix);
        return userConfigRepository.listByUser(tenantId, userId, prefix).stream()
                .map(entry -> new UserConfigVO(entry.configKey(), entry.configValue()))
                .toList();
    }

    /**
     * 读取单条配置值
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     * @return 配置值；未配置返回 empty
     */
    public Optional<String> findValue(Long tenantId, Long userId, String configKey) {
        requireUserScope(tenantId, userId);
        ConfigKeyValidator.requireValidKey(configKey);
        return userConfigRepository.findValue(tenantId, userId, configKey);
    }

    /**
     * upsert 一条用户配置
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param command  保存命令
     */
    public void upsert(Long tenantId, Long userId, SaveUserConfigCommand command) {
        requireUserScope(tenantId, userId);
        ConfigKeyValidator.requireValidKey(command.configKey());
        ConfigKeyValidator.requireValidValue(command.configValue());
        userConfigRepository.upsert(tenantId, userId,
                command.configKey(), command.configValue(), command.updatedBy());
    }

    /**
     * 删除一条用户配置（删除后读取时回到租户默认/消费方默认）
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     */
    public void delete(Long tenantId, Long userId, String configKey) {
        requireUserScope(tenantId, userId);
        ConfigKeyValidator.requireValidKey(configKey);
        userConfigRepository.delete(tenantId, userId, configKey);
    }

    /**
     * 防御校验租户/用户作用域参数：接口层上下文缺失时尽早 400，避免空值透传仓储层
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     */
    private static void requireUserScope(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "租户/用户上下文缺失，无法定位用户配置");
        }
    }

}
