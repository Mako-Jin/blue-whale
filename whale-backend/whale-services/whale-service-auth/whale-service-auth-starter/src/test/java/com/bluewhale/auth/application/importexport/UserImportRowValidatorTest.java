package com.bluewhale.auth.application.importexport;

import com.bluewhale.auth.application.model.importexport.ImportRowError;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.application.service.impl.UserImportRowValidator;
import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.domain.aggregate.RoleAggregate;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link UserImportRowValidator} 行校验器参数化用例（权限中心扩展 M4）。
 *
 * <p>覆盖必填/格式/批内重复/DB 批量查重/部门与角色存在性；错误须定位到「行 + 列」。</p>
 *
 * @author BlueWhale
 */
class UserImportRowValidatorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DeptRepository deptRepository = mock(DeptRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);

    private UserImportRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UserImportRowValidator(userRepository, deptRepository, roleRepository);
        TenantContext.set(1L);
        // 默认放行：库内无同名用户、部门不存在校验仅对出现的部门触发
        lenient().when(userRepository.listExistingUsernames(any(), anyCollection())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static UserImportRow row(int rowNo, String username, String phone, String email,
                                     String deptId, String roleIds, String status) {
        return new UserImportRow(rowNo, username, null, phone, email, deptId, roleIds, status);
    }

    private static boolean hasError(List<ImportRowError> errors, int rowNo, String column, String messagePart) {
        return errors.stream().anyMatch(e -> e.rowNo() == rowNo && e.column().equals(column)
                && e.message().contains(messagePart));
    }

    @Test
    void 合法行_无错误() {
        List<ImportRowError> errors = validator.validate(List.of(
                row(2, "zhangsan", "13800138000", "zhangsan@example.com", null, null, "1")));
        assertThat(errors).isEmpty();
    }

    @Test
    void 用户名为空_报必填错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "  ", null, null, null, null, null)));
        assertThat(hasError(errors, 2, "用户名", "不能为空")).isTrue();
    }

    @Test
    void 用户名格式非法_报格式错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(3, "张三!!", null, null, null, null, null)));
        assertThat(hasError(errors, 3, "用户名", "用户名须为")).isTrue();
    }

    @Test
    void 手机号格式非法_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", "12345", null, null, null, null)));
        assertThat(hasError(errors, 2, "手机号", "格式非法")).isTrue();
    }

    @Test
    void 邮箱格式非法_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, "not-an-email", null, null, null)));
        assertThat(hasError(errors, 2, "邮箱", "格式非法")).isTrue();
    }

    @Test
    void 状态非法_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, null, "2")));
        assertThat(hasError(errors, 2, "状态", "仅允许 1")).isTrue();
    }

    @Test
    void 批内重复用户名_第二行报重复() {
        List<ImportRowError> errors = validator.validate(List.of(
                row(2, "zhangsan", null, null, null, null, null),
                row(3, "zhangsan", null, null, null, null, null)));
        assertThat(hasError(errors, 3, "用户名", "与文件内第 2 行用户名重复")).isTrue();
    }

    @Test
    void 库内已存在用户名_报已存在() {
        when(userRepository.listExistingUsernames(any(), anyCollection())).thenReturn(List.of("zhangsan"));
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, null, null)));
        assertThat(hasError(errors, 2, "用户名", "租户内已存在同名用户")).isTrue();
    }

    @Test
    void 部门ID非数字_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, "abc", null, null)));
        assertThat(hasError(errors, 2, "部门ID", "须为数字")).isTrue();
    }

    @Test
    void 部门不存在_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, "999", null, null)));
        assertThat(hasError(errors, 2, "部门ID", "部门不存在")).isTrue();
    }

    @Test
    void 部门存在_无错误() {
        DeptAggregate dept = mock(DeptAggregate.class);
        lenient().when(dept.getId()).thenReturn(100L);
        when(deptRepository.findById(100L)).thenReturn(java.util.Optional.of(dept));
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, "100", null, null)));
        assertThat(errors).isEmpty();
    }

    @Test
    void 角色ID格式非法_报列错误() {
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, "10,x", null)));
        assertThat(hasError(errors, 2, "角色ID", "须为数字")).isTrue();
    }

    @Test
    void 角色不存在_逐行报错() {
        when(roleRepository.countByIds(anyCollection())).thenReturn(0L);
        when(roleRepository.listEnabledByIds(anyCollection())).thenReturn(List.of());
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, "10,11", null)));
        assertThat(hasError(errors, 2, "角色ID", "角色 10 不存在或已停用")).isTrue();
        assertThat(hasError(errors, 2, "角色ID", "角色 11 不存在或已停用")).isTrue();
    }

    @Test
    void 角色全部有效_无错误() {
        RoleAggregate role = mock(RoleAggregate.class);
        lenient().when(role.getId()).thenReturn(10L);
        when(roleRepository.countByIds(anyCollection())).thenReturn(1L);
        when(roleRepository.listEnabledByIds(anyCollection())).thenReturn(List.of(role));
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, "10,10", null)));
        assertThat(errors).isEmpty();
    }

    @Test
    void 查重降级_不阻断预检() {
        when(userRepository.listExistingUsernames(any(), anyCollection())).thenThrow(new RuntimeException("db down"));
        List<ImportRowError> errors = validator.validate(List.of(row(2, "zhangsan", null, null, null, null, null)));
        assertThat(errors).isEmpty();
    }
}
