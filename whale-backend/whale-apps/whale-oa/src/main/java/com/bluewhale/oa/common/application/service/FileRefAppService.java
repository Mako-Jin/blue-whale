package com.bluewhale.oa.common.application.service;

import com.bluewhale.oa.common.application.command.FileRefRegisterCommand;
import com.bluewhale.oa.common.application.view.FileRefView;

import java.util.List;

/**
 * 文件引用应用服务。
 *
 * @author BlueWhale
 */
public interface FileRefAppService {

    /**
     * 查询业务单据下的文件引用。
     *
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     * @return 文件引用列表
     */
    List<FileRefView> listByBiz(String bizType, Long bizId);

    /**
     * 登记文件引用（幂等：同一业务同一文件只保留一条）。
     *
     * @param command 登记命令
     * @return 文件引用视图
     */
    FileRefView register(FileRefRegisterCommand command);

    /**
     * 解除文件引用（逻辑删除）。
     *
     * @param id 文件引用ID
     */
    void remove(Long id);
}
