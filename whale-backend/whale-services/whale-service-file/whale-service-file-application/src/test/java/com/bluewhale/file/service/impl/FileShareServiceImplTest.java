package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAssetAggregate;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.model.vo.FileStatus;
import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.vo.SharePassword;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.metrics.FileMetrics;
import com.bluewhale.file.model.command.CreateShareCommand;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareCreateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileShareServiceImpl} 创建、访问与下载计数单元测试。
 *
 * @author BlueWhale
 */
class FileShareServiceImplTest {

    private static final long FILE_ID = 21L;

    private static final long UPLOADER_ID = 9L;

    private static final String PASSWORD = "whale-1234";

    private final FileShareRepository fileShareRepository = mock(FileShareRepository.class);

    private final FileAssetRepository fileAssetRepository = mock(FileAssetRepository.class);

    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileProperties fileProperties = new FileProperties();

    private final StoragePluginCatalog storagePluginCatalog = mock(StoragePluginCatalog.class);

    private final FileMetrics fileMetrics = new FileMetrics(new SimpleMeterRegistry(),
            mock(ChunkUploadStore.class), storagePluginCatalog);

    private final FileShareServiceImpl fileShareService = new FileShareServiceImpl(fileShareRepository,
            fileAssetRepository, fileStorage, fileProperties, fileMetrics);

    /**
     * 让仓储保存回填入参聚合，并默认存在一个正常文件。
     */
    @BeforeEach
    void setUp() {
        when(storagePluginCatalog.plugins()).thenReturn(List.of());
        when(fileShareRepository.save(any(FileShareAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset(FileStatus.NORMAL)));
    }

    @Test
    @DisplayName("create：未指定有效期取默认 7 天，密码只落哈希")
    void shouldCreateWithDefaultExpiryAndHashedPassword() {
        FileShareCreateResult result = fileShareService.create(new CreateShareCommand(FILE_ID, null, 5,
                PASSWORD, UPLOADER_ID, false));

        ArgumentCaptor<FileShareAggregate> captor = ArgumentCaptor.forClass(FileShareAggregate.class);
        verify(fileShareRepository).save(captor.capture());
        FileShareAggregate saved = captor.getValue();
        assertTrue(saved.requiresPassword());
        assertFalse(PASSWORD.equals(saved.getPasswordHash()));
        assertTrue(SharePassword.matches(PASSWORD, saved.getPasswordHash()));
        assertEquals(5, saved.getMaxDownloads());
        Duration untilExpiry = Duration.between(LocalDateTime.now(), saved.getExpireTime());
        assertTrue(untilExpiry.toHours() >= 167 && untilExpiry.toHours() <= 168);
        assertTrue(result.hasPassword());
        assertEquals(saved.getToken(), result.token());
    }

    @Test
    @DisplayName("create：有效期超过上限时截断到 720 小时")
    void shouldClampExpiryToMaximum() {
        fileShareService.create(new CreateShareCommand(FILE_ID, 99999, null, null, UPLOADER_ID, false));

        ArgumentCaptor<FileShareAggregate> captor = ArgumentCaptor.forClass(FileShareAggregate.class);
        verify(fileShareRepository).save(captor.capture());
        long hours = Duration.between(LocalDateTime.now(), captor.getValue().getExpireTime()).toHours();
        assertTrue(hours >= 719 && hours <= 720);
    }

    @Test
    @DisplayName("create：有效期非正数拒绝，禁用文件拒绝分享")
    void shouldRejectIllegalExpiryAndDisabledFile() {
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> fileShareService.create(new CreateShareCommand(FILE_ID, 0, null, null, UPLOADER_ID, false)))
                .getCode());

        when(fileAssetRepository.findById(FILE_ID)).thenReturn(Optional.of(asset(FileStatus.DISABLED)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> fileShareService.create(new CreateShareCommand(FILE_ID, null, null, null,
                        UPLOADER_ID, false))).getCode());
    }

    @Test
    @DisplayName("create：非上传人且无管理权限时按不存在处理")
    void shouldRejectInvisibleFile() {
        assertEquals(ErrorCode.NOT_FOUND.getCode(), assertThrows(BizException.class,
                () -> fileShareService.create(new CreateShareCommand(FILE_ID, null, null, null, 100L, false)))
                .getCode());
        verify(fileShareRepository, never()).save(any(FileShareAggregate.class));
    }

    @Test
    @DisplayName("access：需密码且未提供时只返回 needPassword，不泄露文件信息")
    void shouldNotLeakFileInfoWithoutPassword() {
        FileShareAggregate share = share(31L, SharePassword.hash(PASSWORD), null, 0, ShareStatus.ACTIVE);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));

        FileShareAccessResult result = fileShareService.access(share.getToken(), null);

        assertTrue(result.needPassword());
        assertNull(result.file());
    }

    @Test
    @DisplayName("access：密码错误抛 FORBIDDEN，令牌非法按失效处理")
    void shouldRejectWrongPasswordAndIllegalToken() {
        FileShareAggregate share = share(31L, SharePassword.hash(PASSWORD), null, 0, ShareStatus.ACTIVE);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), assertThrows(BizException.class,
                () -> fileShareService.access(share.getToken(), "wrong-pass")).getCode());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), assertThrows(BizException.class,
                () -> fileShareService.access("bad token", null)).getCode());
    }

    @Test
    @DisplayName("openContent：次数占用失败抛 NOT_FOUND 且不读取存储")
    void shouldRejectWhenDownloadQuotaExhausted() {
        FileShareAggregate share = share(31L, null, 5, 0, ShareStatus.ACTIVE);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileShareRepository.consumeDownload(share.getId())).thenReturn(false);

        assertEquals(ErrorCode.NOT_FOUND.getCode(), assertThrows(BizException.class,
                () -> fileShareService.openContent(share.getToken(), null, null, null)).getCode());
        verify(fileStorage, never()).load(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("openContent：区间请求按存储类型路由，占用成功后返回内容流")
    void shouldOpenRangeAfterConsumingDownload() {
        FileShareAggregate share = share(31L, null, null, 0, ShareStatus.ACTIVE);
        when(fileShareRepository.findByToken(share.getToken())).thenReturn(Optional.of(share));
        when(fileShareRepository.consumeDownload(share.getId())).thenReturn(true);
        InputStream expected = new ByteArrayInputStream(new byte[]{1, 2});
        when(fileStorage.load("bluewhale", "tenants/1/a.txt", 1L, 2L, "local")).thenReturn(expected);

        InputStream actual = fileShareService.openContent(share.getToken(), null, 1L, 2L);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("revoke：有效分享撤销一次，已撤销不重复更新")
    void shouldRevokeOnlyActiveShare() {
        FileShareAggregate active = share(31L, null, null, 0, ShareStatus.ACTIVE);
        when(fileShareRepository.findById(active.getId())).thenReturn(Optional.of(active));

        fileShareService.revoke(active.getId(), UPLOADER_ID, false);
        verify(fileShareRepository).revoke(active.getId());

        FileShareAggregate revoked = share(32L, null, null, 0, ShareStatus.REVOKED);
        when(fileShareRepository.findById(revoked.getId())).thenReturn(Optional.of(revoked));
        fileShareService.revoke(revoked.getId(), UPLOADER_ID, false);
        verify(fileShareRepository, never()).revoke(revoked.getId());
    }

    /**
     * 构造正常文件资产（对象键带租户前缀）
     *
     * @param status 文件状态
     * @return 文件资产聚合根
     */
    private static FileAssetAggregate asset(FileStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return FileAssetAggregate.reconstitute(FILE_ID, "a.txt", "报表.xlsx", "bluewhale",
                "tenants/1/a.txt", 1024L, "application/vnd.ms-excel", "md5", status, UPLOADER_ID,
                false, now, now, "local");
    }

    /**
     * 构造分享聚合（固定主键与令牌，便于桩查询）
     *
     * @param id 分享ID
     * @param passwordHash 密码哈希，可为 {@code null}
     * @param maxDownloads 次数上限，可为 {@code null}
     * @param downloadCount 已下载次数
     * @param status 分享状态
     * @return 分享聚合根
     */
    private static FileShareAggregate share(long id, String passwordHash, Integer maxDownloads,
                                            int downloadCount, ShareStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return FileShareAggregate.reconstitute(id, FILE_ID, "报表.xlsx", ShareToken.generate().value(),
                passwordHash, null, maxDownloads, downloadCount, status, UPLOADER_ID, now, now);
    }
}
