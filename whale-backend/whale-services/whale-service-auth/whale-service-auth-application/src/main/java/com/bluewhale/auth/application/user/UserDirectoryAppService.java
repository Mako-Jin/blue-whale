package com.bluewhale.auth.application.user;

import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.vo.UserDirectoryVO;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户目录应用服务：面向服务间调用的「用户存在且启用」批量判定、内网通讯录与按角色查用户。
 *
 * <p>仅供内网接口使用：{@code POST /user/internal/exists} 供 IM 等业务服务在创建会话、
 * 添加群成员前校验目标用户是否真实存在；{@code GET /user/internal/directory} 与
 * {@code GET /user/internal/by-role} 供 OA 审批中心解析审批人。所有对外字段均不含口令等敏感信息。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryAppService {

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
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @param current 当前页码，小于 1 时按 1 处理
     * @param size 每页条数，小于 1 按 1 处理，超过 200 按 200 处理
     * @return 通讯录分页结果
     */
    public PageResult<UserDirectoryVO> directory(Long deptId, String keyword, long current, long size) {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        UserRepository.UserPage page = userRepository.directoryPage(safeCurrent, safeSize, deptId, keyword);
        List<UserDirectoryVO> records = toViewObjects(page.records());
        return PageResult.of(records, page.total(), safeCurrent, safeSize);
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
    private List<UserDirectoryVO> toViewObjects(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = users.stream()
                .map(User::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> deptNames = deptNameMap(deptIds);
        return users.stream()
                .map(user -> UserDirectoryVO.from(user, deptNames.get(user.getDeptId())))
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
