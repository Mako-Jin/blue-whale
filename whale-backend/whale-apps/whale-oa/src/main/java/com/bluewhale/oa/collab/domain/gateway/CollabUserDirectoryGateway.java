package com.bluewhale.oa.collab.domain.gateway;

import com.bluewhale.oa.collab.domain.model.UserCandidate;

import java.util.List;

/**
 * 协同通讯录网关：公告接收人候选与全量接收人解析。
 *
 * @author BlueWhale
 */
public interface CollabUserDirectoryGateway {

    /**
     * 查询接收人候选。
     *
     * @param keyword 关键字（账号 / 姓名）
     * @param deptId 部门ID（为空不限）
     * @param limit 最大返回条数
     * @return 候选用户
     */
    List<UserCandidate> listUsers(String keyword, Long deptId, int limit);
}
