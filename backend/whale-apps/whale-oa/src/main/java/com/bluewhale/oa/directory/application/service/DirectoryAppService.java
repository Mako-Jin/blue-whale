package com.bluewhale.oa.directory.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.directory.application.view.DirectoryViews;

import java.util.List;

/**
 * 通讯录应用服务。
 *
 * @author BlueWhale
 */
public interface DirectoryAppService {

    /**
     * 分页查询通讯录用户。
     *
     * @param keyword 关键字
     * @param deptId 部门ID
     * @param current 当前页
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<DirectoryViews.UserView> pageUsers(String keyword, Long deptId, long current, long size);

    /**
     * 查询组织架构树。
     *
     * @return 部门树
     */
    List<DirectoryViews.DeptView> deptTree();
}
