package com.bluewhale.oa.drive.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.drive.application.command.DriveCommands;
import com.bluewhale.oa.drive.application.config.DriveProperties;
import com.bluewhale.oa.drive.application.service.DriveAppService;
import com.bluewhale.oa.drive.application.view.DriveViews;
import com.bluewhale.oa.drive.domain.gateway.DriveDeptGateway;
import com.bluewhale.oa.drive.domain.gateway.DriveFileGateway;
import com.bluewhale.oa.drive.domain.model.DriveAcl;
import com.bluewhale.oa.drive.domain.model.DriveFile;
import com.bluewhale.oa.drive.domain.model.DriveFolder;
import com.bluewhale.oa.drive.domain.model.DriveQuery;
import com.bluewhale.oa.drive.domain.model.DriveScope;
import com.bluewhale.oa.drive.domain.policy.DriveAccessPolicy;
import com.bluewhale.oa.drive.domain.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 网盘应用服务实现。
 *
 * <p>访问控制规则见 {@link DriveAccessPolicy}；所有写操作均要求管理权限，
 * 上传者本人可删除自己的文件登记。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DriveAppServiceImpl implements DriveAppService {

    /** 根目录父ID */
    private static final Long ROOT_PARENT_ID = 0L;

    /** 文件夹名称最大长度 */
    private static final int MAX_FOLDER_NAME_LENGTH = 64;

    /** 单个文件可用状态 */
    private static final int FILE_STATUS_NORMAL = 1;

    /** 未删除标记 */
    private static final int NOT_DELETED = 0;

    private final DriveRepository repository;

    private final DriveFileGateway fileGateway;

    private final DriveDeptGateway deptGateway;

    private final DriveProperties properties;

    @Override
    public List<DriveViews.FolderView> listFolders(Long parentId, Long userId, String username) {
        Long parent = parentId == null ? ROOT_PARENT_ID : parentId;
        Long deptId = deptGateway.deptIdOf(username);
        if (parent != 0) {
            DriveFolder parentFolder = repository.findFolder(parent);
            if (parentFolder == null
                    || !DriveAccessPolicy.canRead(parentFolder, repository.listAcls(parent), userId, deptId)) {
                throw OaErrorCode.exception(OaErrorCode.DRIVE_FOLDER_NOT_FOUND);
            }
        }
        return repository.listFolders(parent, userId, deptId).stream()
                .filter(folder -> DriveAccessPolicy.canRead(folder, repository.listAcls(folder.getId()), userId, deptId))
                .map(folder -> toFolderView(folder,
                        DriveAccessPolicy.canWrite(folder, repository.listAcls(folder.getId()), userId, deptId),
                        DriveAccessPolicy.canManage(folder, repository.listAcls(folder.getId()), userId, deptId)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriveViews.FolderView createFolder(DriveCommands.FolderCreateCommand command, Long userId, String username) {
        DriveScope scope = DriveScope.of(command.getScope());
        Long parentId = command.getParentId() == null ? ROOT_PARENT_ID : command.getParentId();
        Long deptId = DriveScope.DEPARTMENT == scope ? command.getDeptId() : null;
        String name = requireFolderName(command.getName());
        if (parentId != 0) {
            DriveFolder parent = repository.findFolder(parentId);
            if (parent == null) {
                throw OaErrorCode.exception(OaErrorCode.DRIVE_FOLDER_NOT_FOUND);
            }
            Long userDept = deptGateway.deptIdOf(username);
            if (!DriveAccessPolicy.canManage(parent, repository.listAcls(parentId), userId, userDept)) {
                throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
            }
        } else if (DriveScope.DEPARTMENT == scope) {
            Long userDept = deptGateway.deptIdOf(username);
            if (userDept == null || deptId == null || !deptId.equals(userDept)) {
                throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
            }
        }
        if (folderNameExists(parentId, name, scope, userId, deptId, null)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_NAME_EXISTS);
        }
        DriveFolder folder = new DriveFolder();
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setScope(scope.name());
        folder.setDeptId(deptId);
        folder.setOwnerId(userId);
        folder.setOwnerName(username);
        folder.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        repository.saveFolder(folder);
        return toFolderView(folder, true, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriveViews.FolderView updateFolder(Long id, DriveCommands.FolderUpdateCommand command,
                                              Long userId, String username) {
        DriveFolder folder = requireFolder(id);
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canManage(folder, repository.listAcls(id), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        String name = requireFolderName(command.getName());
        if (folderNameExists(folder.getParentId(), name, DriveScope.of(folder.getScope()),
                folder.getOwnerId(), folder.getDeptId(), id)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_NAME_EXISTS);
        }
        folder.setName(name);
        if (command.getSortNo() != null) {
            folder.setSortNo(command.getSortNo());
        }
        repository.updateFolder(folder);
        return toFolderView(folder, true, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long id, Long userId, String username) {
        DriveFolder folder = requireFolder(id);
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canManage(folder, repository.listAcls(id), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        if (!repository.listChildFolders(id).isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FOLDER_NOT_EMPTY);
        }
        DriveQuery query = new DriveQuery();
        query.setFolderId(id);
        query.setCurrent(1);
        query.setSize(1);
        if (repository.listFiles(query).getTotal() > 0) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FOLDER_NOT_EMPTY);
        }
        repository.deleteAcls(id);
        repository.deleteFolder(id);
    }

    @Override
    public PageResult<DriveViews.FileView> pageFiles(DriveQuery query, Long userId, String username) {
        if (query.getFolderId() == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "查询文件必须指定文件夹");
        }
        DriveFolder folder = requireFolder(query.getFolderId());
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canRead(folder, repository.listAcls(folder.getId()), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        PageResult<DriveFile> page = repository.listFiles(query);
        List<DriveViews.FileView> records = page.getRecords().stream()
                .map(DriveAppServiceImpl::toFileView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DriveViews.FileView registerFile(DriveCommands.FileRegisterCommand command, Long userId, String username) {
        if (command.getFileId() == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "文件ID不能为空");
        }
        DriveFolder folder = requireFolder(command.getFolderId());
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canWrite(folder, repository.listAcls(folder.getId()), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        DriveFileGateway.FileMeta meta = fileGateway.findFile(command.getFileId())
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.DRIVE_FILE_NOT_FOUND));
        if (meta.deleted() == null || meta.deleted() != NOT_DELETED
                || meta.status() == null || meta.status() != FILE_STATUS_NORMAL) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_UNAVAILABLE);
        }
        if (!Objects.equals(meta.uploaderId(), userId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        if (repository.findFileByFolderAndFile(command.getFolderId(), command.getFileId()) != null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_EXISTS);
        }
        long fileSize = meta.fileSize() == null ? 0L : meta.fileSize();
        long used = repository.sumFileSizeByUploader(userId);
        if (used + fileSize > quotaBytes()) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_QUOTA_EXCEEDED);
        }
        DriveFile file = new DriveFile();
        file.setFolderId(command.getFolderId());
        file.setFileId(command.getFileId());
        file.setFileName(meta.originalName());
        file.setFileSize(fileSize);
        file.setContentType(meta.contentType());
        file.setUploaderId(userId);
        file.setUploaderName(username);
        repository.saveFile(file);
        return toFileView(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id, Long userId, String username) {
        DriveFile file = repository.findFile(id);
        if (file == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_NOT_FOUND);
        }
        DriveFolder folder = repository.findFolder(file.getFolderId());
        if (folder == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_NOT_FOUND);
        }
        Long deptId = deptGateway.deptIdOf(username);
        boolean manageable = DriveAccessPolicy.canManage(folder, repository.listAcls(folder.getId()), userId, deptId);
        if (!manageable && !Objects.equals(file.getUploaderId(), userId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        repository.deleteFile(id);
    }

    @Override
    public DriveViews.DownloadView download(Long id, Long userId, String username) {
        DriveFile file = repository.findFile(id);
        if (file == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_NOT_FOUND);
        }
        DriveFolder folder = repository.findFolder(file.getFolderId());
        if (folder == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_NOT_FOUND);
        }
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canRead(folder, repository.listAcls(folder.getId()), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        byte[] content = fileGateway.content(file.getFileId());
        if (content == null || content.length == 0) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FILE_UNAVAILABLE);
        }
        DriveViews.DownloadView view = new DriveViews.DownloadView();
        view.setId(file.getId());
        view.setFileName(file.getFileName());
        view.setContentType(file.getContentType());
        view.setFileSize(file.getFileSize());
        view.setResource(new ByteArrayResource(content));
        return view;
    }

    @Override
    public List<DriveViews.AclView> listAcls(Long folderId, Long userId, String username) {
        DriveFolder folder = requireFolder(folderId);
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canManage(folder, repository.listAcls(folderId), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        return repository.listAcls(folderId).stream().map(DriveAppServiceImpl::toAclView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DriveViews.AclView> replaceAcls(Long folderId, DriveCommands.AclSaveCommand command,
                                                Long userId, String username) {
        DriveFolder folder = requireFolder(folderId);
        Long deptId = deptGateway.deptIdOf(username);
        if (!DriveAccessPolicy.canManage(folder, repository.listAcls(folderId), userId, deptId)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FORBIDDEN);
        }
        repository.deleteAcls(folderId);
        Set<String> keys = new HashSet<>();
        List<DriveAcl> saved = new ArrayList<>();
        if (command.getAcls() != null) {
            for (DriveCommands.AclItemCommand item : command.getAcls()) {
                String subjectType = normalizeSubjectType(item.getSubjectType());
                String effect = normalizeEffect(item.getEffect());
                String permission = normalizePermission(item.getPermission());
                if (item.getSubjectId() == null) {
                    throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "授权主体ID不能为空");
                }
                if (DriveAccessPolicy.SUBJECT_USER.equals(subjectType)
                        && item.getSubjectId().equals(folder.getOwnerId())) {
                    continue;
                }
                String key = subjectType + ":" + item.getSubjectId() + ":" + effect;
                if (!keys.add(key)) {
                    continue;
                }
                DriveAcl acl = new DriveAcl();
                acl.setFolderId(folderId);
                acl.setSubjectType(subjectType);
                acl.setSubjectId(item.getSubjectId());
                acl.setSubjectName(item.getSubjectName());
                acl.setEffect(effect);
                acl.setPermission(permission);
                repository.saveAcl(acl);
                saved.add(acl);
            }
        }
        return saved.stream().map(DriveAppServiceImpl::toAclView).toList();
    }

    @Override
    public DriveViews.QuotaView quota(Long userId) {
        DriveViews.QuotaView view = new DriveViews.QuotaView();
        view.setUsedBytes(repository.sumFileSizeByUploader(userId));
        view.setTotalBytes(quotaBytes());
        return view;
    }

    /**
     * 读取文件夹，不存在时按不存在处理。
     *
     * @param id 文件夹ID
     * @return 文件夹
     */
    private DriveFolder requireFolder(Long id) {
        DriveFolder folder = id == null ? null : repository.findFolder(id);
        if (folder == null) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_FOLDER_NOT_FOUND);
        }
        return folder;
    }

    /**
     * 校验并规范化文件夹名称。
     *
     * @param name 原始名称
     * @return 规范化名称
     */
    private static String requireFolderName(String name) {
        if (!StringUtils.hasText(name)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "文件夹名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_FOLDER_NAME_LENGTH) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "文件夹名称不能超过 64 个字符");
        }
        return trimmed;
    }

    /**
     * 判断同级目录下是否已存在同名文件夹。
     *
     * @param parentId  父文件夹ID
     * @param name      名称
     * @param scope     范围
     * @param ownerId   所有者ID
     * @param deptId    部门ID
     * @param excludeId 排除的文件夹ID
     * @return 是否存在
     */
    private boolean folderNameExists(Long parentId, String name, DriveScope scope,
                                     Long ownerId, Long deptId, Long excludeId) {
        return repository.listChildFolders(parentId).stream()
                .filter(folder -> excludeId == null || !excludeId.equals(folder.getId()))
                .filter(folder -> name.equals(folder.getName()))
                .anyMatch(folder -> DriveScope.DEPARTMENT == scope
                        ? deptId != null && deptId.equals(folder.getDeptId())
                        : ownerId != null && ownerId.equals(folder.getOwnerId()));
    }

    /**
     * 计算配额字节数。
     *
     * @return 配额字节数
     */
    private long quotaBytes() {
        return properties.getQuotaMb() * 1024L * 1024L;
    }

    /**
     * 校验并规范化授权主体类型。
     *
     * @param value 原始值
     * @return 规范化值
     */
    private static String normalizeSubjectType(String value) {
        String normalized = normalize(value, "授权主体类型");
        if (!DriveAccessPolicy.SUBJECT_USER.equals(normalized) && !DriveAccessPolicy.SUBJECT_DEPT.equals(normalized)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "授权主体类型仅支持 USER / DEPT");
        }
        return normalized;
    }

    /**
     * 校验并规范化授权效果。
     *
     * @param value 原始值
     * @return 规范化值
     */
    private static String normalizeEffect(String value) {
        String normalized = normalize(value, "授权效果");
        if (!DriveAccessPolicy.EFFECT_ALLOW.equals(normalized) && !DriveAccessPolicy.EFFECT_DENY.equals(normalized)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "授权效果仅支持 ALLOW / DENY");
        }
        return normalized;
    }

    /**
     * 校验并规范化授权权限。
     *
     * @param value 原始值
     * @return 规范化值
     */
    private static String normalizePermission(String value) {
        String normalized = normalize(value, "授权权限");
        if (DriveAccessPolicy.levelOf(normalized) == 0) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "授权权限仅支持 READ / WRITE / MANAGE");
        }
        return normalized;
    }

    /**
     * 规范化枚举文本。
     *
     * @param value 原始值
     * @param label 字段名
     * @return 大写文本
     */
    private static String normalize(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, label + "不能为空");
        }
        return value.trim().toUpperCase();
    }

    /**
     * 将文件夹模型转换为文件夹视图。
     *
     * @param folder 文件夹模型
     * @param writable 当前用户是否可写
     * @param manageable 当前用户是否可管理
     * @return 文件夹视图
     */
    private static DriveViews.FolderView toFolderView(DriveFolder folder, boolean writable, boolean manageable) {
        DriveViews.FolderView view = new DriveViews.FolderView();
        view.setId(folder.getId());
        view.setParentId(folder.getParentId());
        view.setName(folder.getName());
        view.setScope(folder.getScope());
        view.setDeptId(folder.getDeptId());
        view.setOwnerId(folder.getOwnerId());
        view.setOwnerName(folder.getOwnerName());
        view.setSortNo(folder.getSortNo());
        view.setCreateTime(folder.getCreateTime());
        view.setWritable(writable);
        view.setManageable(manageable);
        return view;
    }

    /**
     * 将文件模型转换为文件视图。
     *
     * @param file 文件模型
     * @return 文件视图
     */
    private static DriveViews.FileView toFileView(DriveFile file) {
        DriveViews.FileView view = new DriveViews.FileView();
        view.setId(file.getId());
        view.setFolderId(file.getFolderId());
        view.setFileId(file.getFileId());
        view.setFileName(file.getFileName());
        view.setFileSize(file.getFileSize());
        view.setContentType(file.getContentType());
        view.setUploaderId(file.getUploaderId());
        view.setUploaderName(file.getUploaderName());
        view.setCreateTime(file.getCreateTime());
        return view;
    }

    /**
     * 将授权模型转换为授权视图。
     *
     * @param acl 授权模型
     * @return 授权视图
     */
    private static DriveViews.AclView toAclView(DriveAcl acl) {
        DriveViews.AclView view = new DriveViews.AclView();
        view.setSubjectType(acl.getSubjectType());
        view.setSubjectId(acl.getSubjectId());
        view.setSubjectName(acl.getSubjectName());
        view.setEffect(acl.getEffect());
        view.setPermission(acl.getPermission());
        return view;
    }
}
