package com.bluewhale.oa.drive.domain.repository;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.drive.domain.model.DriveAcl;
import com.bluewhale.oa.drive.domain.model.DriveFile;
import com.bluewhale.oa.drive.domain.model.DriveFolder;
import com.bluewhale.oa.drive.domain.model.DriveQuery;

import java.util.List;

/**
 * 网盘仓储。
 *
 * @author BlueWhale
 */
public interface DriveRepository {

    /**
     * 查询文件夹。
     *
     * @param id 文件夹ID
     * @return 文件夹；不存在返回 null
     */
    DriveFolder findFolder(Long id);

    /**
     * 查询指定父目录下当前用户的候选文件夹（所有者、部门成员或显式允许）。
     *
     * <p>拒绝授权由应用层过滤，此处仅返回候选集合。</p>
     *
     * @param parentId 父文件夹ID（0 表示根）
     * @param userId   当前用户ID
     * @param deptId   当前用户部门ID（可为空）
     * @return 候选文件夹
     */
    List<DriveFolder> listFolders(Long parentId, Long userId, Long deptId);

    /**
     * 查询全部直接子文件夹（用于非空校验与同名校验）。
     *
     * @param parentId 父文件夹ID
     * @return 子文件夹
     */
    List<DriveFolder> listChildFolders(Long parentId);

    /**
     * 分页查询文件夹内文件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DriveFile> listFiles(DriveQuery query);

    /**
     * 查询文件登记记录。
     *
     * @param id 记录ID
     * @return 文件；不存在返回 null
     */
    DriveFile findFile(Long id);

    /**
     * 按文件夹与文件ID查询登记记录。
     *
     * @param folderId 文件夹ID
     * @param fileId   whale-file 文件ID
     * @return 文件；不存在返回 null
     */
    DriveFile findFileByFolderAndFile(Long folderId, Long fileId);

    /**
     * 新增文件夹并回填主键。
     *
     * @param folder 文件夹
     */
    void saveFolder(DriveFolder folder);

    /**
     * 更新文件夹（仅名称与排序）。
     *
     * @param folder 文件夹
     */
    void updateFolder(DriveFolder folder);

    /**
     * 逻辑删除文件夹。
     *
     * @param id 文件夹ID
     */
    void deleteFolder(Long id);

    /**
     * 新增文件登记并回填主键。
     *
     * @param file 文件
     */
    void saveFile(DriveFile file);

    /**
     * 逻辑删除文件登记。
     *
     * @param id 记录ID
     */
    void deleteFile(Long id);

    /**
     * 查询文件夹的全部授权。
     *
     * @param folderId 文件夹ID
     * @return 授权
     */
    List<DriveAcl> listAcls(Long folderId);

    /**
     * 删除文件夹的全部授权。
     *
     * @param folderId 文件夹ID
     */
    void deleteAcls(Long folderId);

    /**
     * 新增授权。
     *
     * @param acl 授权
     */
    void saveAcl(DriveAcl acl);

    /**
     * 查询当前用户或部门在所有文件夹上的匹配授权。
     *
     * @param userId 当前用户ID
     * @param deptId 当前用户部门ID（可为空）
     * @return 匹配授权
     */
    List<DriveAcl> listAclsFor(Long userId, Long deptId);

    /**
     * 汇总上传人名下已登记文件字节数。
     *
     * @param userId 上传人用户ID
     * @return 字节数
     */
    long sumFileSizeByUploader(Long userId);
}
