package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.model.vo.UserConfigVO;
import com.bluewhale.auth.application.port.UserConfigRepository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 用户配置应用服务：用户自助管理自己的配置（无权限码要求，登录即可）。
 *
 * <p>配置按「当前租户」隔离：同一用户在不同租户下的配置相互独立。</p>
 *
 * @author BlueWhale
 */
public interface UserConfigAppService {

    /**
     * 列出当前用户在指定租户下的配置（可按前缀过滤）
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param prefix   配置键前缀，空表示不过滤
     * @return 配置列表（按 config_key 升序）
     */
    List<UserConfigVO> list(Long tenantId, Long userId, String prefix);

    /**
     * 读取单条配置值
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     * @return 配置值；未配置返回 empty
     */
    Optional<String> findValue(Long tenantId, Long userId, String configKey);

    /**
     * upsert 一条用户配置
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param command  保存命令
     */
    void upsert(Long tenantId, Long userId, SaveUserConfigCommand command);

    /**
     * 删除一条用户配置（删除后读取时回到租户默认/消费方默认）
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     */
    void delete(Long tenantId, Long userId, String configKey);
}
