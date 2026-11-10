package com.bluewhale.oa.drive.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.drive.application.command.DriveCommands;
import com.bluewhale.oa.drive.application.view.DriveViews;
import com.bluewhale.oa.drive.domain.model.DriveQuery;

import java.util.List;

/**
 * 网盘应用服务。
 *
 * @author BlueWhale
 */
public interface DriveAppService {

    /**
     * 查询目录下的可见文件夹。
     *
     * @param parentId 父文件夹ID（0 表示根）
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 文件夹
     */
    List<DriveViews.FolderView> listFolders(Long parentId, Long userId, String username);

    /**
     * 新建文件夹。
     *
     * @param command  创建命令
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 文件夹
     */
    DriveViews.FolderView createFolder(DriveCommands.FolderCreateCommand command, Long userId, String username);

    /**
     * 修改文件夹名称与排序。
     *
     * @param id       文件夹ID
     * @param command  修改命令
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 文件夹
     */
    DriveViews.FolderView updateFolder(Long id, DriveCommands.FolderUpdateCommand command,
                                       Long userId, String username);

    /**
     * 删除空文件夹。
     *
     * @param id       文件夹ID
     * @param userId   当前用户ID
     * @param username 当前用户账号
     */
    void deleteFolder(Long id, Long userId, String username);

    /**
     * 分页查询文件夹内文件。
     *
     * @param query    查询条件
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 分页结果
     */
    PageResult<DriveViews.FileView> pageFiles(DriveQuery query, Long userId, String username);

    /**
     * 登记已上传文件。
     *
     * @param command  登记命令
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 文件
     */
    DriveViews.FileView registerFile(DriveCommands.FileRegisterCommand command, Long userId, String username);

    /**
     * 删除文件登记。
     *
     * @param id       记录ID
     * @param userId   当前用户ID
     * @param username 当前用户账号
     */
    void deleteFile(Long id, Long userId, String username);

    /**
     * 下载文件。
     *
     * @param id       记录ID
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 下载视图
     */
    DriveViews.DownloadView download(Long id, Long userId, String username);

    /**
     * 查询文件夹授权。
     *
     * @param folderId 文件夹ID
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 授权
     */
    List<DriveViews.AclView> listAcls(Long folderId, Long userId, String username);

    /**
     * 整体替换文件夹授权。
     *
     * @param folderId 文件夹ID
     * @param command  授权命令
     * @param userId   当前用户ID
     * @param username 当前用户账号
     * @return 最新授权
     */
    List<DriveViews.AclView> replaceAcls(Long folderId, DriveCommands.AclSaveCommand command,
                                         Long userId, String username);

    /**
     * 查询当前用户网盘配额。
     *
     * @param userId 当前用户ID
     * @return 配额
     */
    DriveViews.QuotaView quota(Long userId);
}
