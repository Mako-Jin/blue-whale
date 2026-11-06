package com.bluewhale.mail.service;

import com.bluewhale.mail.dto.MailRuleSaveRequest;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.entity.MailRule;
import com.bluewhale.mail.vo.MailRuleApplyVO;
import com.bluewhale.mail.vo.MailRuleVO;

import java.util.List;

/**
 * 收信规则服务。
 *
 * <p>规则只在平台侧改写邮件的<b>归类位置</b>（{@code mail_message.folder_id}），
 * 不改动服务器上的邮件：真实位置记在 {@code imap_folder_id}，所以归类之后
 * 正文、附件与标记回源仍然指向正确的那一封。</p>
 *
 * @author BlueWhale
 */
public interface MailRuleService {

    /**
     * 列出账号下的规则
     *
     * @param accountId 账号ID，为空取默认账号
     * @param userId 当前用户ID
     * @return 规则列表（按优先级升序）
     */
    List<MailRuleVO> list(Long accountId, Long userId);

    /**
     * 新建或更新规则
     *
     * @param request 保存请求
     * @param userId 当前用户ID
     * @return 保存后的规则
     */
    MailRuleVO save(MailRuleSaveRequest request, Long userId);

    /**
     * 删除规则（逻辑删除）
     *
     * @param id 规则ID
     * @param userId 当前用户ID
     */
    void remove(Long id, Long userId);

    /**
     * 启用或停用规则
     *
     * @param id 规则ID
     * @param enabled 是否启用
     * @param userId 当前用户ID
     */
    void setEnabled(Long id, Boolean enabled, Long userId);

    /**
     * 载入账号下启用的规则，按优先级升序
     *
     * <p>同步流程每轮只查一次，逐封套用；不要按封查——一轮几百封就是几百次查询。</p>
     *
     * @param accountId 账号ID
     * @return 启用中的规则
     */
    List<MailRule> loadEnabled(Long accountId);

    /**
     * 对单封邮件套用规则，命中的第一个即生效
     *
     * <p>只改写内存中的实体，不落库：调用方（同步或回填）自己决定何时写、写哪些字段。</p>
     *
     * @param rules 启用中的规则
     * @param message 邮件（会被改写归类与标记）
     * @return 命中的规则；未命中返回 {@code null}
     */
    MailRule match(List<MailRule> rules, MailMessage message);

    /**
     * 对收件箱存量邮件回填规则
     *
     * <p>天然幂等：被归类的邮件已不在收件箱，重跑不会重复处理。</p>
     *
     * @param accountId 账号ID，为空取默认账号
     * @param userId 当前用户ID
     * @return 执行结果
     */
    MailRuleApplyVO applyToInbox(Long accountId, Long userId);

    /**
     * 记录规则命中（累计封数与最近命中时间）
     *
     * @param rule 规则
     */
    void recordHit(MailRule rule);
}
