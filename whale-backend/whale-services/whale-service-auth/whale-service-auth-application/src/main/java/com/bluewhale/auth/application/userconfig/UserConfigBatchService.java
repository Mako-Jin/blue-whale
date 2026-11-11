package com.bluewhale.auth.application.userconfig;

import com.bluewhale.auth.application.userconfig.port.TenantConfigRepository;
import com.bluewhale.auth.application.userconfig.port.UserConfigRepository;
import com.bluewhale.auth.application.userconfig.vo.ConfigBatchItemVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户配置批量合并读取服务（供 whale-message 等服务经内网端点调用）。
 *
 * <p>合并优先级：用户配置 &gt; 租户配置 &gt; 未配置（value 为 null、source=NONE）。
 * 纯查询，不修改任何状态；调用方拿到结果后自行解释业务语义。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserConfigBatchService {

    /** 单次批量读取的用户数上限 */
    public static final int MAX_USERS_PER_REQUEST = 200;

    /** 单次批量读取的配置键数上限 */
    public static final int MAX_KEYS_PER_REQUEST = 50;

    /** 用户配置仓储 */
    private final UserConfigRepository userConfigRepository;

    /** 租户配置仓储 */
    private final TenantConfigRepository tenantConfigRepository;

    /**
     * 批量读取多个用户在多个配置键上的生效值（含租户默认合并）
     *
     * @param tenantId 租户 ID
     * @param userIds  用户 ID 列表（去重，上限 {@value #MAX_USERS_PER_REQUEST}）
     * @param keys     配置键列表（去重，上限 {@value #MAX_KEYS_PER_REQUEST}）
     * @return 每个 (userId, configKey) 组合一个结果项
     */
    public List<ConfigBatchItemVO> batchGet(Long tenantId, List<Long> userIds, List<String> keys) {
        List<Long> distinctUsers = distinct(userIds, MAX_USERS_PER_REQUEST, "用户数超上限");
        List<String> distinctKeys = distinct(keys, MAX_KEYS_PER_REQUEST, "配置键数超上限");
        if (distinctUsers.isEmpty() || distinctKeys.isEmpty()) {
            return List.of();
        }
        distinctKeys.forEach(ConfigKeyValidator::requireValidKey);

        Map<String, String> tenantValues = tenantConfigRepository.findValues(tenantId, distinctKeys);
        List<ConfigBatchItemVO> items = new ArrayList<>();
        for (Long userId : distinctUsers) {
            for (String key : distinctKeys) {
                items.add(resolveItem(tenantId, userId, key, tenantValues));
            }
        }
        return items;
    }

    /**
     * 解析单个 (userId, configKey) 的生效值：用户层 &gt; 租户层 &gt; 未配置
     *
     * @param tenantId     租户 ID
     * @param userId       用户 ID
     * @param configKey    配置键
     * @param tenantValues 租户默认值集合（本请求内只查一次）
     * @return 结果项
     */
    private ConfigBatchItemVO resolveItem(Long tenantId, Long userId, String configKey,
                                          Map<String, String> tenantValues) {
        String userValue = userConfigRepository
                .findValue(tenantId, userId, configKey)
                .orElse(null);
        if (userValue != null) {
            return new ConfigBatchItemVO(userId, configKey, userValue, ConfigBatchItemVO.SOURCE_USER);
        }
        String tenantValue = tenantValues.get(configKey);
        if (tenantValue != null) {
            return new ConfigBatchItemVO(userId, configKey, tenantValue, ConfigBatchItemVO.SOURCE_TENANT);
        }
        return new ConfigBatchItemVO(userId, configKey, null, ConfigBatchItemVO.SOURCE_NONE);
    }

    /**
     * 去重并校验批量参数上限
     *
     * @param values  原始列表
     * @param max     上限
     * @param message 超限文案
     * @param <T>     元素类型
     * @return 去重后的列表（保持原顺序）
     */
    private static <T> List<T> distinct(List<T> values, int max, String message) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<T> seen = new HashSet<>(values);
        if (seen.size() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR, message + "（上限 " + max + "）");
        }
        return new ArrayList<>(seen);
    }
}
