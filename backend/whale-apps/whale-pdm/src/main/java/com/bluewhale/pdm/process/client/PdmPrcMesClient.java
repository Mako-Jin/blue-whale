package com.bluewhale.pdm.process.client;

import com.bluewhale.pdm.process.dto.PdmPrcMesPublishRequest;

/**
 * MES 工艺路线下发客户端
 *
 * <p>调用 MES 内网端点；MES 不可用时抛出异常，由业务层记录失败并允许重试。</p>
 *
 * @author BlueWhale
 */
public interface PdmPrcMesClient {

    /**
     * 下发工艺路线。
     *
     * @param tenantId 租户
     * @param request  下发内容
     * @return MES 侧路线标识
     */
    String push(Long tenantId, PdmPrcMesPublishRequest request);

    /**
     * 撤回工艺路线。
     *
     * @param tenantId 租户
     * @param routeNo  路线编号
     * @param versionNo 版本号
     */
    void withdraw(Long tenantId, String routeNo, String versionNo);
}
