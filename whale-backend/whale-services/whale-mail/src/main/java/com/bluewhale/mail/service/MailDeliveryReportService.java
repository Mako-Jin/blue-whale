package com.bluewhale.mail.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.vo.MailDeliveryReportVO;

import java.util.List;

/**
 * 退信回执服务。
 *
 * <p>退信是「发出去但对方没收到」的唯一可靠信号。平台原先只看得到 SMTP 是否接受，
 * 而 SMTP 接受只代表下一跳收下了，不代表最终投递成功——异步退信必须回读才有闭环。</p>
 *
 * @author BlueWhale
 */
public interface MailDeliveryReportService {

    /**
     * 解析一封 DSN 邮件并落库。
     *
     * @param account 邮箱账号
     * @param folder 邮件所在文件夹
     * @param notify 退信通知邮件（已入库的索引行）
     * @return 新增回执条数；非退信或已入库返回 0
     */
    int ingest(MailAccount account, MailFolder folder, MailMessage notify);

    /**
     * 分页查询退信回执。
     *
     * @param accountId 账号ID；为空取当前用户默认账号
     * @param userId 当前用户ID（校验账号归属）
     * @param current 当前页
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<MailDeliveryReportVO> page(Long accountId, Long userId, long current, long size);

    /**
     * 查询某封退信通知对应的回执明细。
     *
     * @param notifyMessageId 退信通知邮件ID
     * @return 回执列表（按收件人）
     */
    List<MailDeliveryReportVO> listByNotify(Long notifyMessageId);
}
