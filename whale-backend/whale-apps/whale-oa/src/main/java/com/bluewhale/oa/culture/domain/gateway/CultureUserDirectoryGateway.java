package com.bluewhale.oa.culture.domain.gateway;

import java.util.Map;

/**
 * 企业文化通讯录网关。
 *
 * <p>仅用于把用户ID补全为姓名快照；目录不可达时降级为空映射。</p>
 *
 * @author BlueWhale
 */
public interface CultureUserDirectoryGateway {

    /**
     * 解析用户ID到姓名。
     *
     * @param limit 最多解析人数
     * @return 用户ID → 姓名；失败返回空映射
     */
    Map<Long, String> resolveUserNames(int limit);
}
