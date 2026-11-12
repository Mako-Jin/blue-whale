package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveOAuthClientCommand;
import com.bluewhale.auth.application.model.query.OAuthClientPageQuery;
import com.bluewhale.auth.application.model.vo.OAuthClientSecretVO;
import com.bluewhale.auth.application.model.vo.OAuthClientVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * OAuth2 客户端应用服务（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
public interface OAuthClientAppService {

    /**
     * 分页查询客户端
     *
     * @param query 分页查询
     * @return 客户端分页结果
     */
    PageResult<OAuthClientVO> page(OAuthClientPageQuery query);

    /**
     * 注册客户端：生成客户端标识与密钥，密钥明文仅返回一次
     *
     * @param command 保存命令
     * @return 含密钥明文的视图对象
     */
    OAuthClientSecretVO create(SaveOAuthClientCommand command);

    /**
     * 编辑客户端配置（不改变客户端标识与密钥）
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的客户端视图对象
     */
    OAuthClientVO update(Long id, SaveOAuthClientCommand command);

    /**
     * 轮换客户端密钥：旧密钥立即失效，新密钥明文仅返回一次
     *
     * @param id 主键
     * @return 含新密钥明文的视图对象
     */
    OAuthClientSecretVO rotateSecret(Long id);

    /**
     * 停用客户端（停用后不得换票）
     *
     * @param id 主键
     */
    void disable(Long id);
}
