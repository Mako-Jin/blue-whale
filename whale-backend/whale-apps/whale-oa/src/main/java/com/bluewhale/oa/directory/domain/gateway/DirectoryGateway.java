package com.bluewhale.oa.directory.domain.gateway;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.directory.domain.model.DeptNode;
import com.bluewhale.oa.directory.domain.model.DirectoryUser;

import java.util.List;

/**
 * 通讯录网关：读取 Auth 内网通讯录与组织架构。
 *
 * @author BlueWhale
 */
public interface DirectoryGateway {

    /**
     * 分页查询通讯录用户。
     *
     * @param keyword 关键字（账号/昵称）
     * @param deptId 部门ID
     * @param current 当前页
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<DirectoryUser> pageUsers(String keyword, Long deptId, long current, long size);

    /**
     * 查询组织架构树。
     *
     * @return 部门树
     */
    List<DeptNode> deptTree();
}
