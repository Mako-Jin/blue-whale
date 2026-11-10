package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.CreatorQueryCommand;
import com.bluewhale.video.application.command.CreatorUpdateCommand;
import com.bluewhale.video.application.view.CreatorAdminView;

/**
 * 创作者运营应用服务：分页查询与停用 / 资料维护。
 *
 * @author BlueWhale
 */
public interface CreatorAppService {

    /**
     * 创作者分页（昵称 / 状态筛选）。
     *
     * @param command 查询命令
     * @return 创作者分页
     */
    PageResult<CreatorAdminView> adminPage(CreatorQueryCommand command);

    /**
     * 编辑创作者资料与状态（停用后不得发布新内容）。
     *
     * @param id 创作者 ID
     * @param command 编辑命令
     * @return 创作者运营视图
     */
    CreatorAdminView update(Long id, CreatorUpdateCommand command);
}
