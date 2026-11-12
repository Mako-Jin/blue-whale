package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;

import java.util.List;

/**
 * 网关白名单应用服务：向网关提供只读下发能力，向管理端提供维护能力。
 *
 * @author BlueWhale
 */
public interface GatewayWhitelistAppService {

    /**
     * 查询全部启用的平台级白名单（按 {@code sort_no} 升序）
     *
     * @return 白名单视图对象列表；无数据时返回空列表
     */
    List<GatewayWhitelistVO> listEnabled();

    /**
     * 管理端查询白名单（含停用项）
     *
     * @param status 状态过滤；{@code null} 表示全部
     * @return 访问名单管理视图对象列表
     */
    List<GatewayAccessRuleVO> list(Integer status);

    /**
     * 新增白名单条目
     *
     * @param command 保存命令
     */
    void create(SaveGatewayAccessRuleCommand command);

    /**
     * 更新白名单条目
     *
     * @param id 主键
     * @param command 保存命令
     */
    void update(Long id, SaveGatewayAccessRuleCommand command);

    /**
     * 删除白名单条目（物理删除，释放唯一键）
     *
     * @param id 主键
     */
    void delete(Long id);
}
