package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.vo.UserDirectoryVO;
import com.bluewhale.auth.application.service.UserDirectoryAppService;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户目录应用服务：面向服务间调用的「用户存在且启用」批量判定、内网通讯录与按角色查用户。
 *
 * <p>仅供内网接口使用：{@code POST /user/v1/internal/exists} 供 IM 等业务服务在创建会话、
 * 添加群成员前校验目标用户是否真实存在；{@code GET /user/v1/internal/directory} 与
 * {@code GET /user/v1/internal/by-role} 供 OA 审批中心解析审批人。所有对外字段均不含口令等敏感信息。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryAppServiceImpl implements UserDirectoryAppService {

    /** 分页每页条数上限 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 按角色查询返回条数上限 */
    private static final int MAX_ROLE_RESULT = 200;

    /** User 聚合仓储 */
    private final UserRepository userRepository;

    /** Dept 聚合仓储（仅用于批量补全通讯录部门名称，避免 N+1） */
    private final DeptRepository deptRepository;

    /**
     * 批量查询启用状态的用户 ID。
     *
     * @param ids 待校验用户 ID（允许重复，内部去重）
     * @return 存在且启用的用户 ID 列表
     */
    public List<Long> findEnabledIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findEnabledIds(new LinkedHashSet<>(ids));
    }

    /**
     * 分页查询内网通讯录：仅启用且未删除的本租户用户，手机号与邮箱脱敏，部门名称批量补全。
     *
     * @param deptId 部门 ID（可空，为空时不过滤）
     * @param includeChildren 是否连同子部门一起查（{@code true} 时常选父部门也能看到下属成员）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @param current 当前页码，小于 1 时按 1 处理
     * @param size 每页条数，小于 1 按 1 处理，超过 200 按 200 处理
     * @return 通讯录分页结果
     */
    public PageResult<UserDirectoryVO> directory(Long deptId, boolean includeChildren, String keyword,
                                                 long current, long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Collection<Long> deptIds = deptScope(deptId, includeChildren);
        if (deptIds != null && deptIds.isEmpty()) {
            // 指定的部门不存在（或没有权限看到的部门）：直接空页，别下推成 `in ()`
            return PageResult.of(List.of(), 0L, safeCurrent, safeSize);
        }
        UserRepository.UserPage page = userRepository.directoryPage(safeCurrent, safeSize, deptIds, keyword);
        List<UserDirectoryVO> records = toViewObjects(page.records());
        return PageResult.of(records, page.total(), safeCurrent, safeSize);
    }

    /**
     * 计算部门筛选范围。
     *
     * <p>{@code includeChildren} 时按部门表的 {@code parentId} 建父子索引后 BFS 逐层展开
     * （单次遍历，O(n)），不依赖返回顺序与层级深度；数据异常成环时也能收敛，因为只加入未见过的 ID。</p>
     *
     * @param deptId 部门 ID（可空）
     * @param includeChildren 是否包含子部门
     * @return 部门 ID 集合；{@code null} 表示不按部门过滤
     */
    private Collection<Long> deptScope(Long deptId, boolean includeChildren) {
        if (deptId == null) {
            return null;
        }
        if (!includeChildren) {
            return List.of(deptId);
        }
        List<Dept> all = deptRepository.findAll();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        boolean exists = false;
        for (Dept dept : all) {
            if (deptId.equals(dept.getId())) {
                exists = true;
            }
            childrenByParent.computeIfAbsent(dept.getParentId(), key -> new java.util.ArrayList<>())
                    .add(dept.getId());
        }
        if (!exists) {
            // 部门已被删除或不在本租户：明确「查不到人」，别退化成「不过滤部门」
            return List.of();
        }
        // BFS 逐层展开子部门（成环时因 scope 去重自动收敛）
        Set<Long> scope = new LinkedHashSet<>();
        scope.add(deptId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            for (Long childId : childrenByParent.getOrDefault(queue.poll(), List.of())) {
                if (scope.add(childId)) {
                    queue.add(childId);
                }
            }
        }
        return scope;
    }

    /**
     * 按角色编码查询内网通讯录：角色不存在或已停用返回空列表，仅返回启用用户。
     *
     * @param roleCode 角色编码
     * @return 通讯录用户列表（按用户 ID 升序，最多 200 条）
     */
    public List<UserDirectoryVO> byRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        return toViewObjects(userRepository.findByRoleCode(roleCode, MAX_ROLE_RESULT));
    }

    /**
     * 将用户聚合批量转换为通讯录视图对象，并一次性补全部门名称
     *
     * @param users 用户聚合列表
     * @return 通讯录视图对象列表
     */
    private List<UserDirectoryVO> toViewObjects(List<UserAggregate> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream()
                .map(UserAggregate::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> deptNames = deptNameMap(deptIds);
        return users.stream()
                // deptNames 可能是 Map.of()（不可变，get(null) 直接 NPE），先挡掉无部门的用户
                .map(user -> UserDirectoryVO.from(user, user.getDeptId() == null
                        ? null : deptNames.get(user.getDeptId())))
                .toList();
    }

    /**
     * 批量查询部门名称（单次全量读取当前租户部门，避免逐用户查询）
     *
     * @param deptIds 需要补全的部门 ID 集合
     * @return 部门 ID → 部门名称映射；入参为空时返回空映射
     */
    private Map<Long, String> deptNameMap(Collection<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return deptRepository.findAll().stream()
                .filter(dept -> deptIds.contains(dept.getId()))
                .collect(Collectors.toMap(Dept::getId, Dept::getDeptName, (first, second) -> first));
    }
}
