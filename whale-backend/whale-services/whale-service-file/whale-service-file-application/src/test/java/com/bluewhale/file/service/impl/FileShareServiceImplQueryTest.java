package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileShareItem;
import com.bluewhale.file.domain.share.model.FileSharePage;
import com.bluewhale.file.domain.share.model.FileShareQuery;
import com.bluewhale.file.domain.share.model.vo.SharePassword;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.metrics.FileMetrics;
import com.bluewhale.file.model.query.FileSharePageQuery;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileShareServiceImpl} 查询/访问路径补充测试。
 *
 * <p>钉住三件容易被忽略的事：① 分页页码与页大小必须夹到合法区间；
 * ② 普通视角按创建人过滤、管理视角不过滤；③ 访问成功时必须把文件资产转换成元数据结果，
 * 且只在真正读存储前占用下载次数。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FileShareServiceImplQueryTest {

    private static final long TENANT_ID = 1L;

    private static final long FILE_ID = 50L;

    private static final long SHARE_ID = 31L;

    private static final long CREATOR_ID = 7L;

    private static final String PASSWORD = "s3cret-pass";

    @Mock
    private FileShareRepository fileShareRepository;

    @Mock
    private FileAssetRepository fileAssetRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileProperties fileProperties;

    @Mock
    private FileMetrics fileMetrics;

    private FileShareServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new FileShareServiceImpl(fileShareRepository, fileAssetRepository, fileStorage,
                fileProperties, fileMetrics);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("page：普通视角按创建人过滤，页码与页大小夹到合法区间，并投影为结果")
    void shouldPageWithCreatorFilterAndClamp() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 10, 0);
        FileShareItem item = new FileShareItem(SHARE_ID, FILE_ID, "报表.xlsx", "token-value",
                now.plusDays(7), 5, 2, 1, CREATOR_ID, now);
        when(fileShareRepository.page(any(FileShareQuery.class)))
                .thenReturn(new FileSharePage(List.of(item), 1L, 1L, 20L));

        PageResult<FileShareResult> result = service.page(
                new FileSharePageQuery(CREATOR_ID, false, 0L, 999L));

        ArgumentCaptor<FileShareQuery> captor = ArgumentCaptor.forClass(FileShareQuery.class);
        verify(fileShareRepository).page(captor.capture());
        assertEquals(CREATOR_ID, captor.getValue().creatorId());
        assertEquals(1L, captor.getValue().current());
        assertEquals(200L, captor.getValue().size());
        assertEquals(1, result.getRecords().size());
        FileShareResult record = result.getRecords().get(0);
        assertEquals(SHARE_ID, record.id());
        assertEquals(FILE_ID, record.fileId());
        assertEquals("报表.xlsx", record.fileName());
        assertEquals("token-value", record.token());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("page：管理视角不过滤创建人，负数页大小夹到 1")
    void shouldPageWithoutCreatorFilterForManager() {
        when(fileShareRepository.page(any(FileShareQuery.class)))
                .thenReturn(new FileSharePage(List.of(), 0L, 1L, 1L));

        service.page(new FileSharePageQuery(CREATOR_ID, true, 3L, -5L));

        ArgumentCaptor<FileShareQuery> captor = ArgumentCaptor.forClass(FileShareQuery.class);
        verify(fileShareRepository).page(captor.capture());
        assertEquals(null, captor.getValue().creatorId());
        assertEquals(3L, captor.getValue().current());
        assertEquals(1L, captor.getValue().size());
    }

    @Test
    @DisplayName("access：无密码分享访问成功时返回文件元数据")
    void shouldReturnFileInfoOnSuccessfulAccess() {
        FileShareAggregate share = share(null);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset()));

        FileShareAccessResult result = service.access(share.getToken(), null);

        assertFalse(result.needPassword());
        assertNotNull(result.file());
        assertEquals(FILE_ID, result.file().id());
        assertEquals("报表.xlsx", result.file().originalName());
        assertEquals("bluewhale", result.file().bucket());
    }

    @Test
    @DisplayName("access：密码正确时返回文件元数据")
    void shouldReturnFileInfoWhenPasswordMatches() {
        FileShareAggregate share = share(SharePassword.hash(PASSWORD));
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset()));

        FileShareAccessResult result = service.access(share.getToken(), PASSWORD);

        assertFalse(result.needPassword());
        assertNotNull(result.file());
    }

    @Test
    @DisplayName("openContent：未指定区间时按整文件加载，成功后记录成功指标")
    void shouldLoadWholeFileWithoutRange() {
        FileShareAggregate share = share(null);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset()));
        when(fileShareRepository.consumeDownload(share.getId())).thenReturn(true);
        InputStream expected = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(fileStorage.load("bluewhale", "tenants/1/a.txt", "local")).thenReturn(expected);

        InputStream actual = service.openContent(share.getToken(), null, null, null);

        assertEquals(expected, actual);
        verify(fileMetrics).recordShareDownload(true);
    }

    @Test
    @DisplayName("openContent：存储读取失败时记录失败指标并原样抛出")
    void shouldRethrowAndRecordFailureWhenStorageFails() {
        FileShareAggregate share = share(null);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset()));
        when(fileShareRepository.consumeDownload(share.getId())).thenReturn(true);
        when(fileStorage.load("bluewhale", "tenants/1/a.txt", "local"))
                .thenThrow(new IllegalStateException("存储不可用"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.openContent(share.getToken(), null, null, null));

        assertEquals("存储不可用", error.getMessage());
        verify(fileMetrics).recordShareDownload(false);
    }

    @Test
    @DisplayName("access：文件已禁用时按失效处理")
    void shouldRejectDisabledFile() {
        FileShareAggregate share = share(null);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset(FileStatus.DISABLED)));

        BizException error = assertThrows(BizException.class,
                () -> service.access(share.getToken(), null));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), error.getCode());
    }

    private static FileShareAggregate share(String passwordHash) {
        return FileShareAggregate.reconstitute(SHARE_ID, FILE_ID, "报表.xlsx",
                ShareToken.generate().value(), passwordHash, null, null, 0,
                ShareStatus.ACTIVE, CREATOR_ID, LocalDateTime.now(), LocalDateTime.now());
    }

    private static FileAssetAggregate asset() {
        return asset(FileStatus.NORMAL);
    }

    private static FileAssetAggregate asset(FileStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return FileAssetAggregate.reconstitute(FILE_ID, "a.txt", "报表.xlsx", "bluewhale",
                "tenants/1/a.txt", 1024L, "application/vnd.ms-excel", "md5", status,
                CREATOR_ID, false, now, now, "local");
    }
}
