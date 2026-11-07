package com.bluewhale.oa.document.application.service;

import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.view.DocumentViews;

import java.util.List;

/**
 * 公文文号应用服务。
 *
 * @author BlueWhale
 */
public interface DocNoAppService {

    /**
     * 查询全部文号规则。
     *
     * @return 规则列表
     */
    List<DocumentViews.NoRule> all();

    /**
     * 新增文号规则。
     *
     * @param command 规则命令
     * @return 规则
     */
    DocumentViews.NoRule create(DocumentCommands.NoRuleSave command);

    /**
     * 更新文号规则。
     *
     * @param id      规则ID
     * @param command 规则命令
     * @return 规则
     */
    DocumentViews.NoRule update(Long id, DocumentCommands.NoRuleSave command);

    /**
     * 按规则取下一个文号。
     *
     * @param ruleCode 规则编码
     * @return 文号
     */
    String nextNo(String ruleCode);
}
