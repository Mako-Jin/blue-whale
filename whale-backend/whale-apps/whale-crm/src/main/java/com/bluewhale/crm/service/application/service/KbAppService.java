package com.bluewhale.crm.service.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.service.application.command.KbRefCommand;
import com.bluewhale.crm.service.application.view.KbRefView;
import com.bluewhale.crm.service.domain.model.KbRefQuery;

import java.util.List;

/**
 * 知识引用应用服务。
 *
 * @author BlueWhale
 */
public interface KbAppService {

    /**
     * 分页查询知识引用。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<KbRefView> page(KbRefQuery query, CrmActor actor);

    /**
     * 查看知识引用详情。
     *
     * @param id 主键
     * @param actor 操作人
     * @return 视图
     */
    KbRefView detail(Long id, CrmActor actor);

    /**
     * 按关键字检索启用中的知识引用（最多 20 条）。
     *
     * @param keyword 关键字
     * @param actor 操作人
     * @return 视图列表
     */
    List<KbRefView> search(String keyword, CrmActor actor);

    /**
     * 新建知识引用。
     *
     * @param command 命令
     * @param actor 操作人
     * @return 视图
     */
    KbRefView create(KbRefCommand command, CrmActor actor);

    /**
     * 更新知识引用。
     *
     * @param id 主键
     * @param command 命令
     * @param actor 操作人
     * @return 视图
     */
    KbRefView update(Long id, KbRefCommand command, CrmActor actor);

    /**
     * 逻辑删除知识引用。
     *
     * @param id 主键
     * @param actor 操作人
     */
    void delete(Long id, CrmActor actor);

    /**
     * 记录引用命中。
     *
     * @param id 主键
     * @param actor 操作人
     * @return 命中后的视图
     */
    KbRefView hit(Long id, CrmActor actor);
}
