package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.importexport.ImportRowError;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户导入行校验器（权限中心扩展 M4）：预检阶段全量校验，错误定位到「行 + 列」。
 *
 * <p>校验维度：必填与格式（用户名/手机号/邮箱/部门 ID/角色 ID/状态）、批内用户名重复、
 * DB 批量查重（单次 {@code in} 查询替代逐行 {@code existsByUsername}）、部门与角色存在性。
 * 全部行级错误一次性返回，不做截断。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserImportRowValidator {

    /** 手机号格式（中国大陆 11 位） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱格式（宽松校验：本地部分@域名） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    /** 数字 ID（部门 ID / 角色 ID） */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d{1,19}$");

    private final UserRepository userRepository;
    private final DeptRepository deptRepository;
    private final RoleRepository roleRepository;

    /**
     * 全量校验导入行
     *
     * @param rows 导入行（保留 Excel 行号）
     * @return 行级错误清单（全部通过时为空列表）
     */
    public List<ImportRowError> validate(List<UserImportRow> rows) {
        List<ImportRowError> errors = new ArrayList<>();
        // 批内重复用户名登记：username → 首次出现的行号
        Map<String, Integer> seen = new LinkedHashMap<>();

        for (UserImportRow row : rows) {
            checkUsername(row, seen, errors);
            checkPhone(row, errors);
            checkEmail(row, errors);
            checkDept(row, errors);
            checkRoles(row, errors);
            checkStatus(row, errors);
        }
        checkExistingUsernames(rows, errors);
        checkDeptExistence(rows, errors);
        checkRoleExistence(rows, errors);
        return errors;
    }

    /**
     * 校验用户名：必填、值对象格式、批内重复
     */
    private void checkUsername(UserImportRow row, Map<String, Integer> seen, List<ImportRowError> errors) {
        String username = trim(row.username());
        if (username == null) {
            errors.add(new ImportRowError(row.rowNo(), "用户名", "不能为空"));
            return;
        }
        try {
            Username.of(username);
        } catch (Exception e) {
            errors.add(new ImportRowError(row.rowNo(), "用户名", e.getMessage()));
        }
        Integer firstRowNo = seen.putIfAbsent(username, row.rowNo());
        if (firstRowNo != null) {
            errors.add(new ImportRowError(row.rowNo(), "用户名",
                    "与文件内第 " + firstRowNo + " 行用户名重复"));
        }
    }

    /**
     * 校验手机号：可空，非空须为中国大陆 11 位手机号
     */
    private void checkPhone(UserImportRow row, List<ImportRowError> errors) {
        String phone = trim(row.phone());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            errors.add(new ImportRowError(row.rowNo(), "手机号", "格式非法，须为 1 开头的 11 位手机号"));
        }
    }

    /**
     * 校验邮箱：可空，非空须为合法邮箱
     */
    private void checkEmail(UserImportRow row, List<ImportRowError> errors) {
        String email = trim(row.email());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new ImportRowError(row.rowNo(), "邮箱", "格式非法"));
        }
    }

    /**
     * 校验部门 ID：可空；非空须为数字
     */
    private void checkDept(UserImportRow row, List<ImportRowError> errors) {
        String deptId = trim(row.deptId());
        if (deptId != null && !NUMBER_PATTERN.matcher(deptId).matches()) {
            errors.add(new ImportRowError(row.rowNo(), "部门ID", "须为数字"));
        }
    }

    /**
     * 校验角色 ID：可空；逗号分隔且每项为数字
     */
    private void checkRoles(UserImportRow row, List<ImportRowError> errors) {
        String roleIds = trim(row.roleIds());
        if (roleIds == null) {
            return;
        }
        for (String part : roleIds.split(",")) {
            String item = part.trim();
            if (item.isEmpty() || !NUMBER_PATTERN.matcher(item).matches()) {
                errors.add(new ImportRowError(row.rowNo(), "角色ID", "「" + item + "」须为数字，多项以英文逗号分隔"));
                return;
            }
        }
    }

    /**
     * 校验状态：可空按启用处理；非空仅允许 1（启用）/ 0（停用）
     */
    private void checkStatus(UserImportRow row, List<ImportRowError> errors) {
        String status = trim(row.status());
        if (status != null && !"1".equals(status) && !"0".equals(status)) {
            errors.add(new ImportRowError(row.rowNo(), "状态", "仅允许 1（启用）/ 0（停用）"));
        }
    }

    /**
     * DB 批量查重：单次 in 查询命中的用户名逐行报错（幂等导入依赖提交侧跳过，预检即提示）
     */
    private void checkExistingUsernames(List<UserImportRow> rows, List<ImportRowError> errors) {
        List<String> usernames = rows.stream()
                .map(row -> trim(row.username()))
                .filter(name -> name != null)
                .distinct()
                .toList();
        if (usernames.isEmpty()) {
            return;
        }
        try {
            Set<String> existing = new HashSet<>(userRepository.listExistingUsernames(
                    TenantContext.requireId(), usernames));
            for (UserImportRow row : rows) {
                String username = trim(row.username());
                if (username != null && existing.contains(username)) {
                    errors.add(new ImportRowError(row.rowNo(), "用户名", "租户内已存在同名用户"));
                }
            }
        } catch (Exception e) {
            // 查重失败不阻断预检（提交侧事务内仍有 existsByUsername + DB 唯一约束兜底）
            log.warn("导入预检批量查重失败，降级跳过: {}", e.getMessage());
        }
    }

    /**
     * 部门存在性检查：不在租户内的部门 ID 逐行报错
     */
    private void checkDeptExistence(List<UserImportRow> rows, List<ImportRowError> errors) {
        Set<Long> deptIds = rows.stream()
                .map(row -> trim(row.deptId()))
                .filter(id -> id != null && NUMBER_PATTERN.matcher(id).matches())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return;
        }
        Set<Long> existing = new HashSet<>();
        for (Long deptId : deptIds) {
            deptRepository.findById(deptId).ifPresent(dept -> existing.add(dept.getId()));
        }
        if (existing.containsAll(deptIds)) {
            return;
        }
        for (UserImportRow row : rows) {
            String deptId = trim(row.deptId());
            if (deptId != null && NUMBER_PATTERN.matcher(deptId).matches()
                    && !existing.contains(Long.parseLong(deptId))) {
                errors.add(new ImportRowError(row.rowNo(), "部门ID", "部门不存在"));
            }
        }
    }

    /**
     * 角色存在性检查：当前租户内不存在的角色 ID 逐行报错
     */
    private void checkRoleExistence(List<UserImportRow> rows, List<ImportRowError> errors) {
        Set<Long> roleIds = rows.stream()
                .flatMap(row -> parseRoleIds(row.roleIds()).stream())
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return;
        }
        // countByIds 统计当前租户内实际存在的角色数；数量一致即全部有效，逐行报错只需标记无效角色
        long existing = roleRepository.countByIds(roleIds);
        if (existing == roleIds.size()) {
            return;
        }
        Set<Long> valid = roleRepository.listEnabledByIds(roleIds).stream()
                .map(role -> role.getId())
                .collect(Collectors.toSet());
        for (UserImportRow row : rows) {
            for (Long roleId : parseRoleIds(row.roleIds())) {
                if (!valid.contains(roleId)) {
                    errors.add(new ImportRowError(row.rowNo(), "角色ID", "角色 " + roleId + " 不存在或已停用"));
                }
            }
        }
    }

    /**
     * 解析角色 ID 单元格为 Long 列表（仅解析合法数字项）
     */
    private static List<Long> parseRoleIds(String raw) {
        String roleIds = trim(raw);
        if (roleIds == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : roleIds.split(",")) {
            String item = part.trim();
            if (!item.isEmpty() && NUMBER_PATTERN.matcher(item).matches()) {
                result.add(Long.parseLong(item));
            }
        }
        return result;
    }

    /**
     * 去空白；全空白视为空返回 {@code null}
     */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
