package com.bluewhale.auth.application.importexport;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.importexport.ExportResult;
import com.bluewhale.auth.application.model.importexport.ImportPreviewResult;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.application.port.ExportFileUploader;
import com.bluewhale.auth.application.port.ImportBatchStore;
import com.bluewhale.auth.application.port.SpreadsheetReader;
import com.bluewhale.auth.application.port.SpreadsheetWriter;
import com.bluewhale.auth.application.service.PasswordPolicyService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.impl.TemporaryPasswordGenerator;
import com.bluewhale.auth.application.service.impl.UserImportExportAppServiceImpl;
import com.bluewhale.auth.application.service.impl.UserImportRowValidator;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserImportExportAppServiceImpl} 用例（权限中心扩展 M4）：
 * 预检暂存/错误不暂存、提交跳过同名、行级失败隔离、分批独立事务、导出直传与上传分支。
 *
 * @author BlueWhale
 */
class UserImportExportAppServiceTest {

    private static final Long TENANT_ID = 1L;
    /** 合法 BCrypt 格式占位密文（满足 EncryptedPassword 不变量校验） */
    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SpreadsheetReader spreadsheetReader = mock(SpreadsheetReader.class);
    private final SpreadsheetWriter spreadsheetWriter = mock(SpreadsheetWriter.class);
    private final ImportBatchStore importBatchStore = mock(ImportBatchStore.class);
    private final ExportFileUploader exportFileUploader = mock(ExportFileUploader.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DeptRepository deptRepository = mock(DeptRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
    private final TenantMembershipAppService tenantMembershipAppService = mock(TenantMembershipAppService.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private UserImportExportAppServiceImpl appService;

    @BeforeEach
    void setUp() {
        UserImportRowValidator validator = new UserImportRowValidator(userRepository, deptRepository, roleRepository);
        appService = new UserImportExportAppServiceImpl(spreadsheetReader, spreadsheetWriter, importBatchStore,
                exportFileUploader, userRepository, validator, passwordPolicyService,
                new TemporaryPasswordGenerator(), tenantMembershipAppService, domainEventPublisher,
                transactionTemplate);
        TenantContext.set(TENANT_ID);
        // TransactionTemplate mock 直接执行回调（真实事务边界由集成环境保证）
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(passwordPolicyService.minLength()).thenReturn(8);
        lenient().when(passwordPolicyService.encode(anyString())).thenReturn(BCRYPT_HASH);
        lenient().when(passwordPolicyService.isForceChangeOnCreate()).thenReturn(true);
        lenient().when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.listExistingUsernames(any(), anyCollection())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static UserImportRow row(int rowNo, String username) {
        return new UserImportRow(rowNo, username, "昵称" + rowNo, "1380013800" + (rowNo % 10),
                null, null, null, "1");
    }

    @Test
    void 模板_返回写出结果() {
        byte[] template = {1, 2, 3};
        when(spreadsheetWriter.writeTemplate()).thenReturn(template);
        assertThat(appService.template()).isSameAs(template);
    }

    @Test
    void 预检全部通过_暂存批次返回batchId() {
        List<UserImportRow> rows = List.of(row(2, "u1"), row(3, "u2"));
        when(spreadsheetReader.readUsers(any(), anyInt())).thenReturn(rows);
        ImportPreviewResult result = appService.preview(new ByteArrayInputStream(new byte[0]));
        assertThat(result.batchId()).isNotBlank();
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.validCount()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        verify(importBatchStore).save(eq(result.batchId()), eq(TENANT_ID), eq(rows), any());
    }

    @Test
    void 预检存在错误_不暂存且batchId为空() {
        List<UserImportRow> rows = List.of(
                new UserImportRow(2, "合法用户名", null, null, null, null, null, "1"),
                new UserImportRow(3, "非法 用户名", null, null, null, null, null, "1"));
        when(spreadsheetReader.readUsers(any(), anyInt())).thenReturn(rows);
        ImportPreviewResult result = appService.preview(new ByteArrayInputStream(new byte[0]));
        assertThat(result.batchId()).isNull();
        assertThat(result.validCount()).isEqualTo(0);
        assertThat(result.errors()).isNotEmpty();
        verify(importBatchStore, never()).save(any(), any(), any(), any());
    }

    @Test
    void 提交批次过期_抛业务异常() {
        when(importBatchStore.load("gone", TENANT_ID)).thenReturn(null);
        assertThatThrownBy(() -> appService.commit("gone"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("导入预检不存在或已过期");
    }

    @Test
    void 提交成功_同名跳过_临时口令与强制改密() {
        List<UserImportRow> rows = List.of(row(2, "newuser"), row(3, "dup"));
        when(importBatchStore.load("batch", TENANT_ID))
                .thenReturn(new ImportBatchStore.StoredBatch(TENANT_ID, rows));
        when(userRepository.existsByUsername(TENANT_ID, "newuser")).thenReturn(false);
        when(userRepository.existsByUsername(TENANT_ID, "dup")).thenReturn(true);

        var result = appService.commit("batch");

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.tempPassword()).isNotBlank();
        ArgumentCaptor<UserAggregate> captor = ArgumentCaptor.forClass(UserAggregate.class);
        verify(userRepository).save(captor.capture());
        UserAggregate saved = captor.getValue();
        assertThat(saved.getRegisterSource()).isEqualTo("IMPORT");
        assertThat(saved.isMustChangePassword()).isTrue();
        verify(importBatchStore).delete("batch");
        verify(passwordPolicyService).recordHistory(eq(TENANT_ID), any(), anyString());
        verify(domainEventPublisher, times(1)).publish(any());
    }

    @Test
    void 提交行级失败_隔离不拖垮整批() {
        List<UserImportRow> rows = List.of(row(2, "bad"), row(3, "good"));
        when(importBatchStore.load("batch", TENANT_ID))
                .thenReturn(new ImportBatchStore.StoredBatch(TENANT_ID, rows));
        when(userRepository.existsByUsername(any(), anyString())).thenReturn(false);
        when(userRepository.save(any(UserAggregate.class))).thenReturn(mock(UserAggregate.class))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 第一行建号抛异常（模拟并发唯一键冲突），第二行正常
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> {
            UserAggregate user = invocation.getArgument(0);
            if ("bad".equals(user.getUsername().value())) {
                throw new RuntimeException("Duplicate entry");
            }
            return user;
        });

        var result = appService.commit("batch");

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
    }

    @Test
    void 提交超过500行_分两个独立事务() {
        List<UserImportRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row(i + 2, "user" + i));
        }
        when(importBatchStore.load("batch", TENANT_ID))
                .thenReturn(new ImportBatchStore.StoredBatch(TENANT_ID, rows));
        when(userRepository.existsByUsername(any(), anyString())).thenReturn(false);
        when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = appService.commit("batch");

        assertThat(result.totalRows()).isEqualTo(501);
        assertThat(result.successCount()).isEqualTo(501);
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void 导出小于上限_直传字节流() {
        UserRepository.UserPage page = new UserRepository.UserPage(List.of(mock(UserAggregate.class)), 2);
        when(userRepository.page(anyLong(), anyLong(), any())).thenReturn(page);
        when(userRepository.listRoleIdsByUserIds(anyCollection())).thenReturn(Map.of());
        when(spreadsheetWriter.writeUsers(any(), any())).thenReturn(2L);

        ExportResult result = appService.export(null);

        assertThat(result.content()).isNotNull();
        assertThat(result.remoteFile()).isNull();
        assertThat(result.totalRows()).isEqualTo(2);
        verify(exportFileUploader, never()).upload(any(), anyString());
    }

    @Test
    void 导出超过上限_上传whaleFile返回objectKey() {
        // 服务实现常量为包私有，测试用同值字面量：同步导出上限 5 万行
        long overLimit = 50_000 + 1;
        UserRepository.UserPage page = new UserRepository.UserPage(List.of(mock(UserAggregate.class)), overLimit);
        when(userRepository.page(anyLong(), anyLong(), any())).thenReturn(page);
        when(userRepository.listRoleIdsByUserIds(anyCollection())).thenReturn(Map.of());
        when(spreadsheetWriter.writeUsers(any(), any())).thenReturn(overLimit);
        when(exportFileUploader.upload(any(), anyString()))
                .thenReturn(Map.of("objectKey", "user-export/users.xlsx", "bucket", "bluewhale-user-export"));

        ExportResult result = appService.export(null);

        assertThat(result.content()).isNull();
        assertThat(result.remoteFile()).containsEntry("objectKey", "user-export/users.xlsx");
        verify(exportFileUploader).upload(any(), anyString());
    }

    @Test
    void 导出无数据_抛业务异常() {
        when(userRepository.page(anyLong(), anyLong(), any()))
                .thenReturn(new UserRepository.UserPage(List.of(), 0));
        assertThatThrownBy(() -> appService.export(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可导出的用户数据");
    }
}
