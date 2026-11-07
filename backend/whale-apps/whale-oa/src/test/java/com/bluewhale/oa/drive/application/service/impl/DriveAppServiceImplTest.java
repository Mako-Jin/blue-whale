package com.bluewhale.oa.drive.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.drive.application.command.DriveCommands;
import com.bluewhale.oa.drive.application.config.DriveProperties;
import com.bluewhale.oa.drive.application.view.DriveViews;
import com.bluewhale.oa.drive.domain.gateway.DriveDeptGateway;
import com.bluewhale.oa.drive.domain.gateway.DriveFileGateway;
import com.bluewhale.oa.drive.domain.model.DriveAcl;
import com.bluewhale.oa.drive.domain.model.DriveFile;
import com.bluewhale.oa.drive.domain.model.DriveFolder;
import com.bluewhale.oa.drive.domain.model.DriveQuery;
import com.bluewhale.oa.drive.domain.policy.DriveAccessPolicy;
import com.bluewhale.oa.drive.domain.repository.DriveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网盘应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DriveAppServiceImplTest {

    private static final Long USER_ID = 8L;

    private static final String USERNAME = "张三";

    @Mock
    private DriveRepository repository;

    @Mock
    private DriveFileGateway fileGateway;

    @Mock
    private DriveDeptGateway deptGateway;

    private DriveProperties properties;

    private DriveAppServiceImpl driveAppService;

    @BeforeEach
    void setUp() {
        properties = new DriveProperties();
        properties.setQuotaMb(1L);
        driveAppService = new DriveAppServiceImpl(repository, fileGateway, deptGateway, properties);
    }

    @Test
    @DisplayName("createFolder：非法范围拒绝")
    void shouldRejectInvalidScope() {
        DriveCommands.FolderCreateCommand command = folderCommand("资料", "TEAM", null, null);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.createFolder(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("createFolder：同级同名拒绝")
    void shouldRejectDuplicateFolderName() {
        DriveFolder existing = folder(2L, 0L, "资料", "PERSONAL", null, USER_ID);
        when(repository.listChildFolders(0L)).thenReturn(List.of(existing));

        DriveCommands.FolderCreateCommand command = folderCommand("资料", "PERSONAL", null, 0L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.createFolder(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_NAME_EXISTS, exception.getCode());
        verify(repository, never()).saveFolder(any());
    }

    @Test
    @DisplayName("createFolder：父文件夹无管理权限拒绝")
    void shouldRejectCreateUnderUnmanageableParent() {
        DriveFolder parent = folder(2L, 0L, "共享", "PERSONAL", null, 9L);
        when(repository.findFolder(2L)).thenReturn(parent);
        when(repository.listAcls(2L)).thenReturn(List.of());

        DriveCommands.FolderCreateCommand command = folderCommand("资料", "PERSONAL", null, 2L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.createFolder(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("createFolder：部门文件夹与当前用户部门不一致时拒绝")
    void shouldRejectDepartmentFolderWithMismatchedDept() {
        when(deptGateway.deptIdOf(USERNAME)).thenReturn(30L);
        DriveCommands.FolderCreateCommand command = folderCommand("部门资料", "DEPARTMENT", 31L, 0L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.createFolder(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("listFolders：DENY 优先于 ALLOW")
    void shouldExcludeDeniedFolderFromList() {
        DriveFolder allowed = folder(1L, 0L, "可读", "PERSONAL", null, 9L);
        DriveFolder denied = folder(2L, 0L, "拒绝", "PERSONAL", null, 9L);
        when(repository.listFolders(eq(0L), eq(USER_ID), any())).thenReturn(List.of(allowed, denied));
        when(repository.listAcls(1L)).thenReturn(List.of(
                acl(1L, DriveAccessPolicy.SUBJECT_USER, USER_ID,
                        DriveAccessPolicy.EFFECT_ALLOW, DriveAccessPolicy.PERMISSION_READ)));
        when(repository.listAcls(2L)).thenReturn(List.of(
                acl(2L, DriveAccessPolicy.SUBJECT_USER, USER_ID,
                        DriveAccessPolicy.EFFECT_ALLOW, DriveAccessPolicy.PERMISSION_READ),
                acl(2L, DriveAccessPolicy.SUBJECT_USER, USER_ID,
                        DriveAccessPolicy.EFFECT_DENY, DriveAccessPolicy.PERMISSION_READ)));

        List<DriveViews.FolderView> folders = driveAppService.listFolders(0L, USER_ID, USERNAME);

        assertEquals(1, folders.size());
        assertEquals(1L, folders.get(0).getId());
    }

    @Test
    @DisplayName("listFolders：部门成员可读但不可写")
    void shouldAllowDeptMemberReadWithoutWrite() {
        when(deptGateway.deptIdOf(USERNAME)).thenReturn(30L);
        DriveFolder deptFolder = folder(5L, 0L, "部门资料", "DEPARTMENT", 30L, 9L);
        when(repository.listFolders(0L, USER_ID, 30L)).thenReturn(List.of(deptFolder));
        when(repository.listAcls(5L)).thenReturn(List.of());

        List<DriveViews.FolderView> folders = driveAppService.listFolders(0L, USER_ID, USERNAME);

        assertEquals(1, folders.size());
        assertFalse(folders.get(0).isWritable());
        assertFalse(folders.get(0).isManageable());
    }

    @Test
    @DisplayName("registerFile：登记他人上传的文件拒绝")
    void shouldRejectRegisteringOthersFile() {
        DriveFolder folder = folder(1L, 0L, "我的资料", "PERSONAL", null, USER_ID);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());
        when(fileGateway.findFile(100L)).thenReturn(Optional.of(
                meta(100L, 9L, 10L, 0, 1)));

        DriveCommands.FileRegisterCommand command = registerCommand(1L, 100L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.registerFile(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("registerFile：同文件夹重复登记拒绝")
    void shouldRejectDuplicateRegistration() {
        DriveFolder folder = folder(1L, 0L, "我的资料", "PERSONAL", null, USER_ID);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());
        when(fileGateway.findFile(100L)).thenReturn(Optional.of(
                meta(100L, USER_ID, 10L, 0, 1)));
        when(repository.findFileByFolderAndFile(1L, 100L)).thenReturn(new DriveFile());

        DriveCommands.FileRegisterCommand command = registerCommand(1L, 100L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.registerFile(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FILE_EXISTS, exception.getCode());
    }

    @Test
    @DisplayName("registerFile：配额超限拒绝")
    void shouldRejectWhenQuotaExceeded() {
        DriveFolder folder = folder(1L, 0L, "我的资料", "PERSONAL", null, USER_ID);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());
        when(fileGateway.findFile(100L)).thenReturn(Optional.of(
                meta(100L, USER_ID, 100L, 0, 1)));
        long quota = properties.getQuotaMb() * 1024L * 1024L;
        when(repository.sumFileSizeByUploader(USER_ID)).thenReturn(quota - 10L);

        DriveCommands.FileRegisterCommand command = registerCommand(1L, 100L);

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.registerFile(command, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_QUOTA_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("deleteFolder：非空文件夹拒绝删除")
    void shouldRejectDeletingNonEmptyFolder() {
        DriveFolder folder = folder(1L, 0L, "我的资料", "PERSONAL", null, USER_ID);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());
        when(repository.listChildFolders(1L)).thenReturn(List.of(folder(2L, 1L, "子目录", "PERSONAL", null, USER_ID)));

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.deleteFolder(1L, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FOLDER_NOT_EMPTY, exception.getCode());
        verify(repository, never()).deleteFolder(anyLong());
    }

    @Test
    @DisplayName("deleteFile：上传者可删除自己的登记")
    void shouldAllowUploaderToDeleteOwnFile() {
        DriveFile file = file(10L, 1L, 100L, 8L);
        DriveFolder folder = folder(1L, 0L, "共享", "PERSONAL", null, 9L);
        when(repository.findFile(10L)).thenReturn(file);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());

        driveAppService.deleteFile(10L, USER_ID, USERNAME);

        verify(repository).deleteFile(10L);
    }

    @Test
    @DisplayName("replaceAcls：过滤所有者自身授权并去重")
    void shouldFilterOwnerWhenReplacingAcls() {
        DriveFolder folder = folder(1L, 0L, "我的资料", "PERSONAL", null, USER_ID);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());

        DriveCommands.AclSaveCommand command = new DriveCommands.AclSaveCommand();
        command.setAcls(List.of(
                aclCommand(DriveAccessPolicy.SUBJECT_USER, USER_ID, DriveAccessPolicy.EFFECT_ALLOW,
                        DriveAccessPolicy.PERMISSION_MANAGE),
                aclCommand(DriveAccessPolicy.SUBJECT_USER, 9L, DriveAccessPolicy.EFFECT_ALLOW,
                        DriveAccessPolicy.PERMISSION_WRITE),
                aclCommand(DriveAccessPolicy.SUBJECT_USER, 9L, DriveAccessPolicy.EFFECT_ALLOW,
                        DriveAccessPolicy.PERMISSION_WRITE)));

        List<DriveViews.AclView> views = driveAppService.replaceAcls(1L, command, USER_ID, USERNAME);

        assertEquals(1, views.size());
        assertEquals(9L, views.get(0).getSubjectId());
        verify(repository).deleteAcls(1L);
        verify(repository, times(1)).saveAcl(any());
    }

    @Test
    @DisplayName("download：无读取权限拒绝")
    void shouldRejectDownloadWithoutReadPermission() {
        DriveFile file = file(10L, 1L, 100L, 9L);
        DriveFolder folder = folder(1L, 0L, "他人资料", "PERSONAL", null, 9L);
        when(repository.findFile(10L)).thenReturn(file);
        when(repository.findFolder(1L)).thenReturn(folder);
        when(repository.listAcls(1L)).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.download(10L, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_FORBIDDEN, exception.getCode());
        verify(fileGateway, never()).content(anyLong());
    }

    @Test
    @DisplayName("pageFiles：缺少文件夹ID拒绝")
    void shouldRejectPageWithoutFolder() {
        DriveQuery query = new DriveQuery();

        BizException exception = assertThrows(BizException.class,
                () -> driveAppService.pageFiles(query, USER_ID, USERNAME));

        assertEquals(OaErrorCode.DRIVE_INVALID, exception.getCode());
    }

    private static DriveCommands.FolderCreateCommand folderCommand(
            String name, String scope, Long deptId, Long parentId) {
        DriveCommands.FolderCreateCommand command = new DriveCommands.FolderCreateCommand();
        command.setName(name);
        command.setScope(scope);
        command.setDeptId(deptId);
        command.setParentId(parentId);
        return command;
    }

    private static DriveCommands.FileRegisterCommand registerCommand(Long folderId, Long fileId) {
        DriveCommands.FileRegisterCommand command = new DriveCommands.FileRegisterCommand();
        command.setFolderId(folderId);
        command.setFileId(fileId);
        return command;
    }

    private static DriveCommands.AclItemCommand aclCommand(String subjectType, Long subjectId,
                                                          String effect, String permission) {
        DriveCommands.AclItemCommand command = new DriveCommands.AclItemCommand();
        command.setSubjectType(subjectType);
        command.setSubjectId(subjectId);
        command.setSubjectName("授权对象");
        command.setEffect(effect);
        command.setPermission(permission);
        return command;
    }

    private static DriveFolder folder(Long id, Long parentId, String name, String scope,
                                      Long deptId, Long ownerId) {
        DriveFolder folder = new DriveFolder();
        folder.setId(id);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setScope(scope);
        folder.setDeptId(deptId);
        folder.setOwnerId(ownerId);
        folder.setOwnerName("张三");
        folder.setSortNo(0);
        return folder;
    }

    private static DriveAcl acl(Long folderId, String subjectType, Long subjectId,
                                String effect, String permission) {
        DriveAcl acl = new DriveAcl();
        acl.setFolderId(folderId);
        acl.setSubjectType(subjectType);
        acl.setSubjectId(subjectId);
        acl.setEffect(effect);
        acl.setPermission(permission);
        return acl;
    }

    private static DriveFile file(Long id, Long folderId, Long fileId, Long uploaderId) {
        DriveFile file = new DriveFile();
        file.setId(id);
        file.setFolderId(folderId);
        file.setFileId(fileId);
        file.setUploaderId(uploaderId);
        return file;
    }

    private static DriveFileGateway.FileMeta meta(Long fileId, Long uploaderId, long size,
                                                  int deleted, int status) {
        return new DriveFileGateway.FileMeta(fileId, "说明.pdf", size, "application/pdf",
                uploaderId, deleted, status);
    }
}
