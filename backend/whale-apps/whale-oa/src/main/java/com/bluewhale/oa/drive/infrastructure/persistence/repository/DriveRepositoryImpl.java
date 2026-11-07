package com.bluewhale.oa.drive.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.drive.domain.model.DriveAcl;
import com.bluewhale.oa.drive.domain.model.DriveFile;
import com.bluewhale.oa.drive.domain.model.DriveFolder;
import com.bluewhale.oa.drive.domain.model.DriveQuery;
import com.bluewhale.oa.drive.domain.model.DriveScope;
import com.bluewhale.oa.drive.domain.policy.DriveAccessPolicy;
import com.bluewhale.oa.drive.domain.repository.DriveRepository;
import com.bluewhale.oa.drive.infrastructure.persistence.entity.DriveAclEntity;
import com.bluewhale.oa.drive.infrastructure.persistence.entity.DriveFileEntity;
import com.bluewhale.oa.drive.infrastructure.persistence.entity.DriveFolderEntity;
import com.bluewhale.oa.drive.infrastructure.persistence.mapper.DriveAclMapper;
import com.bluewhale.oa.drive.infrastructure.persistence.mapper.DriveFileMapper;
import com.bluewhale.oa.drive.infrastructure.persistence.mapper.DriveFolderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 网盘仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class DriveRepositoryImpl implements DriveRepository {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final DriveFolderMapper folderMapper;

    private final DriveFileMapper fileMapper;

    private final DriveAclMapper aclMapper;

    @Override
    public DriveFolder findFolder(Long id) {
        DriveFolderEntity entity = folderMapper.selectById(id);
        return entity == null ? null : toFolder(entity);
    }

    @Override
    public List<DriveFolder> listFolders(Long parentId, Long userId, Long deptId) {
        List<DriveFolder> children = folderMapper.selectList(new LambdaQueryWrapper<DriveFolderEntity>()
                        .eq(DriveFolderEntity::getParentId, parentId)
                        .orderByAsc(DriveFolderEntity::getSortNo)
                        .orderByAsc(DriveFolderEntity::getId))
                .stream().map(DriveRepositoryImpl::toFolder).toList();
        Set<Long> allowedFolderIds = new HashSet<>();
        for (DriveAcl acl : listAclsFor(userId, deptId)) {
            if (DriveAccessPolicy.EFFECT_ALLOW.equals(acl.getEffect())) {
                allowedFolderIds.add(acl.getFolderId());
            }
        }
        return children.stream()
                .filter(folder -> visibleAsCandidate(folder, userId, deptId, allowedFolderIds))
                .toList();
    }

    @Override
    public List<DriveFolder> listChildFolders(Long parentId) {
        return folderMapper.selectList(new LambdaQueryWrapper<DriveFolderEntity>()
                        .eq(DriveFolderEntity::getParentId, parentId)
                        .orderByAsc(DriveFolderEntity::getSortNo)
                        .orderByAsc(DriveFolderEntity::getId))
                .stream().map(DriveRepositoryImpl::toFolder).toList();
    }

    @Override
    public PageResult<DriveFile> listFiles(DriveQuery query) {
        LambdaQueryWrapper<DriveFileEntity> wrapper = new LambdaQueryWrapper<DriveFileEntity>()
                .eq(DriveFileEntity::getFolderId, query.getFolderId())
                .like(StringUtils.hasText(query.getKeyword()), DriveFileEntity::getFileName, query.getKeyword())
                .orderByDesc(DriveFileEntity::getCreateTime)
                .orderByDesc(DriveFileEntity::getId);
        Page<DriveFileEntity> page = fileMapper.selectPage(page(query), wrapper);
        List<DriveFile> records = page.getRecords().stream().map(DriveRepositoryImpl::toFile).toList();
        Long total = fileMapper.selectCount(wrapper);
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public DriveFile findFile(Long id) {
        DriveFileEntity entity = fileMapper.selectById(id);
        return entity == null ? null : toFile(entity);
    }

    @Override
    public DriveFile findFileByFolderAndFile(Long folderId, Long fileId) {
        return fileMapper.selectList(new LambdaQueryWrapper<DriveFileEntity>()
                        .eq(DriveFileEntity::getFolderId, folderId)
                        .eq(DriveFileEntity::getFileId, fileId)
                        .orderByAsc(DriveFileEntity::getId))
                .stream().findFirst().map(DriveRepositoryImpl::toFile).orElse(null);
    }

    @Override
    public void saveFolder(DriveFolder folder) {
        DriveFolderEntity entity = new DriveFolderEntity();
        fill(entity, folder);
        folderMapper.insert(entity);
        folder.setId(entity.getId());
        folder.setCreateTime(entity.getCreateTime());
        folder.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void updateFolder(DriveFolder folder) {
        DriveFolderEntity entity = new DriveFolderEntity();
        entity.setId(folder.getId());
        entity.setName(folder.getName());
        entity.setSortNo(folder.getSortNo());
        folderMapper.updateById(entity);
    }

    @Override
    public void deleteFolder(Long id) {
        folderMapper.deleteById(id);
    }

    @Override
    public void saveFile(DriveFile file) {
        DriveFileEntity entity = new DriveFileEntity();
        fill(entity, file);
        fileMapper.insert(entity);
        file.setId(entity.getId());
        file.setCreateTime(entity.getCreateTime());
        file.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void deleteFile(Long id) {
        fileMapper.deleteById(id);
    }

    @Override
    public List<DriveAcl> listAcls(Long folderId) {
        return aclMapper.selectList(new LambdaQueryWrapper<DriveAclEntity>()
                        .eq(DriveAclEntity::getFolderId, folderId)
                        .orderByAsc(DriveAclEntity::getId))
                .stream().map(DriveRepositoryImpl::toAcl).toList();
    }

    @Override
    public void deleteAcls(Long folderId) {
        aclMapper.delete(new LambdaQueryWrapper<DriveAclEntity>()
                .eq(DriveAclEntity::getFolderId, folderId));
    }

    @Override
    public void saveAcl(DriveAcl acl) {
        DriveAclEntity entity = new DriveAclEntity();
        entity.setFolderId(acl.getFolderId());
        entity.setSubjectType(acl.getSubjectType());
        entity.setSubjectId(acl.getSubjectId());
        entity.setSubjectName(acl.getSubjectName());
        entity.setEffect(acl.getEffect());
        entity.setPermission(acl.getPermission());
        aclMapper.insert(entity);
        acl.setId(entity.getId());
        acl.setCreateTime(entity.getCreateTime());
    }

    @Override
    public List<DriveAcl> listAclsFor(Long userId, Long deptId) {
        if (userId == null && deptId == null) {
            return List.of();
        }
        LambdaQueryWrapper<DriveAclEntity> wrapper = new LambdaQueryWrapper<DriveAclEntity>()
                .and(item -> {
                    if (userId == null) {
                        item.eq(DriveAclEntity::getSubjectType, DriveAccessPolicy.SUBJECT_DEPT)
                                .eq(DriveAclEntity::getSubjectId, deptId);
                    } else if (deptId == null) {
                        item.eq(DriveAclEntity::getSubjectType, DriveAccessPolicy.SUBJECT_USER)
                                .eq(DriveAclEntity::getSubjectId, userId);
                    } else {
                        item.eq(DriveAclEntity::getSubjectType, DriveAccessPolicy.SUBJECT_USER)
                                .eq(DriveAclEntity::getSubjectId, userId)
                                .or(next -> next.eq(DriveAclEntity::getSubjectType, DriveAccessPolicy.SUBJECT_DEPT)
                                        .eq(DriveAclEntity::getSubjectId, deptId));
                    }
                });
        return aclMapper.selectList(wrapper).stream().map(DriveRepositoryImpl::toAcl).toList();
    }

    @Override
    public long sumFileSizeByUploader(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return fileMapper.selectList(new LambdaQueryWrapper<DriveFileEntity>()
                        .eq(DriveFileEntity::getUploaderId, userId))
                .stream()
                .map(DriveFileEntity::getFileSize)
                .filter(size -> size != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private Page<DriveFileEntity> page(DriveQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        return new Page<>(Math.max(query.getCurrent(), 1L), size, false);
    }

    private static boolean visibleAsCandidate(DriveFolder folder, Long userId, Long deptId,
                                              Set<Long> allowedFolderIds) {
        if (userId != null && folder.getOwnerId() != null && folder.getOwnerId().equals(userId)) {
            return true;
        }
        if (deptId != null && DriveScope.DEPARTMENT.name().equals(folder.getScope())
                && deptId.equals(folder.getDeptId())) {
            return true;
        }
        return allowedFolderIds.contains(folder.getId());
    }

    private static DriveFolder toFolder(DriveFolderEntity entity) {
        DriveFolder folder = new DriveFolder();
        folder.setId(entity.getId());
        folder.setParentId(entity.getParentId());
        folder.setName(entity.getName());
        folder.setScope(entity.getScope());
        folder.setDeptId(entity.getDeptId());
        folder.setOwnerId(entity.getOwnerId());
        folder.setOwnerName(entity.getOwnerName());
        folder.setSortNo(entity.getSortNo());
        folder.setCreateTime(entity.getCreateTime());
        folder.setUpdateTime(entity.getUpdateTime());
        return folder;
    }

    private static void fill(DriveFolderEntity entity, DriveFolder folder) {
        entity.setParentId(folder.getParentId());
        entity.setName(folder.getName());
        entity.setScope(folder.getScope());
        entity.setDeptId(folder.getDeptId());
        entity.setOwnerId(folder.getOwnerId());
        entity.setOwnerName(folder.getOwnerName());
        entity.setSortNo(folder.getSortNo());
    }

    private static DriveFile toFile(DriveFileEntity entity) {
        DriveFile file = new DriveFile();
        file.setId(entity.getId());
        file.setFolderId(entity.getFolderId());
        file.setFileId(entity.getFileId());
        file.setFileName(entity.getFileName());
        file.setFileSize(entity.getFileSize());
        file.setContentType(entity.getContentType());
        file.setUploaderId(entity.getUploaderId());
        file.setUploaderName(entity.getUploaderName());
        file.setCreateTime(entity.getCreateTime());
        file.setUpdateTime(entity.getUpdateTime());
        return file;
    }

    private static void fill(DriveFileEntity entity, DriveFile file) {
        entity.setFolderId(file.getFolderId());
        entity.setFileId(file.getFileId());
        entity.setFileName(file.getFileName());
        entity.setFileSize(file.getFileSize());
        entity.setContentType(file.getContentType());
        entity.setUploaderId(file.getUploaderId());
        entity.setUploaderName(file.getUploaderName());
    }

    private static DriveAcl toAcl(DriveAclEntity entity) {
        DriveAcl acl = new DriveAcl();
        acl.setId(entity.getId());
        acl.setFolderId(entity.getFolderId());
        acl.setSubjectType(entity.getSubjectType());
        acl.setSubjectId(entity.getSubjectId());
        acl.setSubjectName(entity.getSubjectName());
        acl.setEffect(entity.getEffect());
        acl.setPermission(entity.getPermission());
        acl.setCreateTime(entity.getCreateTime());
        return acl;
    }
}
