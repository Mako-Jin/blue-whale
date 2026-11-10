package com.bluewhale.oa.administrative.domain.gateway;

import java.util.List;

/**
 * Contract 印章授权查询网关。
 *
 * @author BlueWhale
 */
public interface ContractSealGateway {

    /**
     * 判断用户对指定印章是否有使用授权。
     *
     * @param sealId 印章ID
     * @param userId 用户ID
     * @return 是否有授权
     */
    boolean isGranted(Long sealId, Long userId);

    /**
     * 查询用户可用印章列表。
     *
     * @param userId 用户ID
     * @return 印章列表
     */
    List<SealInfo> grantedSeals(Long userId);

    /**
     * 印章简要信息。
     *
     * @param id 印章ID
     * @param name 印章名称
     */
    record SealInfo(Long id, String name) {
    }
}
