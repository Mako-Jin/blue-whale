package com.bluewhale.oa.directory.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.directory.application.service.DirectoryAppService;
import com.bluewhale.oa.directory.application.view.DirectoryViews;
import com.bluewhale.oa.directory.domain.gateway.DirectoryGateway;
import com.bluewhale.oa.directory.domain.model.DeptNode;
import com.bluewhale.oa.directory.domain.model.DirectoryUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通讯录应用服务实现。
 *
 * <p>分页参数在此归一化：{@code current >= 1}、{@code size} 限制在 1–200；
 * 网关异常统一转为 {@link OaErrorCode#DIRECTORY_UNAVAILABLE}，已是业务异常则直接透传。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DirectoryAppServiceImpl implements DirectoryAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final DirectoryGateway gateway;

    @Override
    public PageResult<DirectoryViews.UserView> pageUsers(
            String keyword, Long deptId, long current, long size) {
        long normalizedCurrent = Math.max(current, 1L);
        long normalizedSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        PageResult<DirectoryUser> page;
        try {
            page = gateway.pageUsers(keyword, deptId, normalizedCurrent, normalizedSize);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw OaErrorCode.exception(OaErrorCode.DIRECTORY_UNAVAILABLE, "通讯录服务不可用");
        }
        if (page == null) {
            return PageResult.of(List.of(), 0L, normalizedCurrent, normalizedSize);
        }
        List<DirectoryViews.UserView> records = page.getRecords().stream()
                .map(DirectoryAppServiceImpl::toUserView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<DirectoryViews.DeptView> deptTree() {
        List<DeptNode> nodes;
        try {
            nodes = gateway.deptTree();
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw OaErrorCode.exception(OaErrorCode.DIRECTORY_UNAVAILABLE, "通讯录服务不可用");
        }
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream().map(DirectoryAppServiceImpl::toDeptView).toList();
    }

    /**
     * 用户视图转换。
     *
     * @param user 通讯录用户
     * @return 视图
     */
    private static DirectoryViews.UserView toUserView(DirectoryUser user) {
        DirectoryViews.UserView view = new DirectoryViews.UserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setDeptId(user.getDeptId());
        view.setDeptName(user.getDeptName());
        view.setPhone(user.getPhone());
        view.setEmail(user.getEmail());
        return view;
    }

    /**
     * 部门树视图转换（递归）。
     *
     * @param node 部门节点
     * @return 视图
     */
    private static DirectoryViews.DeptView toDeptView(DeptNode node) {
        DirectoryViews.DeptView view = new DirectoryViews.DeptView();
        view.setId(node.getId());
        view.setParentId(node.getParentId());
        view.setDeptName(node.getName());
        view.setChildren(node.getChildren() == null ? List.of()
                : node.getChildren().stream().map(DirectoryAppServiceImpl::toDeptView).toList());
        return view;
    }
}
