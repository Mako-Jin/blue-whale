package com.bluewhale.oa.culture.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;

/**
 * 建议箱应用服务。
 *
 * @author BlueWhale
 */
public interface SuggestionAppService {

    /**
     * 建议分页（{@code mine=true} 仅看自己提交）。
     *
     * @param query 查询条件
     * @param userId 当前用户ID
     * @return 分页结果
     */
    PageResult<CultureViews.Suggestion> page(CultureQuery query, Long userId);

    /**
     * 建议详情（含回复，匿名隐藏提交人）。
     *
     * @param id 建议ID
     * @return 详情
     */
    CultureViews.SuggestionDetail detail(Long id);

    /**
     * 提交建议。
     *
     * @param command 提交命令
     * @param submitterId 提交人用户ID
     * @param submitterName 提交人姓名
     * @return 建议
     */
    CultureViews.Suggestion create(CultureCommands.SuggestionSave command, Long submitterId, String submitterName);

    /**
     * 回复建议（状态置已回复）。
     *
     * @param id 建议ID
     * @param command 回复命令
     * @param handlerId 处理人用户ID
     * @param handlerName 处理人姓名
     * @return 建议
     */
    CultureViews.Suggestion reply(Long id, CultureCommands.SuggestionReplySave command, Long handlerId,
                                  String handlerName);

    /**
     * 关闭建议。
     *
     * @param id 建议ID
     */
    void close(Long id);
}
