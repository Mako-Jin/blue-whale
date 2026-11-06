package com.bluewhale.oa.approval.domain.gateway;

import java.util.Optional;

/**
 * 用户目录查询端口。
 *
 * <p>通知接收人必须使用用户ID，OA 审批只持有审批人账号，因此需要按账号精确解析。</p>
 *
 * @author BlueWhale
 */
public interface UserDirectoryGateway {

    /**
     * 按账号解析用户ID。
     *
     * @param username 账号
     * @return 用户ID；账号不存在或无法精确匹配时为空
     */
    Optional<Long> findUserIdByUsername(String username);
}
