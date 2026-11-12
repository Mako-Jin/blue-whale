package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.vo.ConfigBatchItemVO;
import com.bluewhale.auth.application.port.TenantConfigRepository;
import com.bluewhale.auth.application.port.UserConfigRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

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
public interface UserConfigBatchService {

    /** 单次批量读取的用户数上限 */
    public static final int MAX_USERS_PER_REQUEST = 200;

    /** 单次批量读取的配置键数上限 */
    public static final int MAX_KEYS_PER_REQUEST = 50;

    /**
     * 批量读取多个用户在多个配置键上的生效值（含租户默认合并）
     *
     * @param tenantId 租户 ID
     * @param userIds  用户 ID 列表（去重，上限 {@value #MAX_USERS_PER_REQUEST}）
     * @param keys     配置键列表（去重，上限 {@value #MAX_KEYS_PER_REQUEST}）
     * @return 每个 (userId, configKey) 组合一个结果项
     */
    List<ConfigBatchItemVO> batchGet(Long tenantId, List<Long> userIds, List<String> keys);
}
