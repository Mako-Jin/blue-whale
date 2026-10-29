package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.vo.DocWsTicketVO;
import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocWsTicket;
import tools.jackson.databind.JsonNode;

/**
 * 文档实时协作应用服务（M2）。
 *
 * <p>WS 仅中继 Yjs 更新，不解析文档结构；服务端定时接收领袖客户端的完整正文快照，
 * 复用 M1 乐观锁写入与版本快照能力落库。REST 保存发现页面存在活跃协作会话时返回
 * {@code 40902}，前端应改用协作快照保存。</p>
 *
 * @author BlueWhale
 */
public interface DocCollabService {

    /**
     * 签发一次性 WS ticket。
     *
     * @param pageId   页面ID
     * @param userId   用户ID
     * @param username 用户名
     * @return ticket 与有效期
     */
    DocWsTicketVO issueTicket(Long pageId, Long userId, String username);

    /**
     * 原子消费一次性 WS ticket。
     *
     * @param ticket 票据
     * @return 票据载荷
     */
    DocWsTicket consumeTicket(String ticket);

    /**
     * 连接初始化：读取页面快照与 Yjs 更新日志。
     *
     * @param ticket 已消费的票据
     * @return 初始化数据
     */
    DocCollabInit load(DocWsTicket ticket);

    /**
     * 判断页面是否存在活跃协作会话。
     *
     * @param pageId 页面ID
     * @return 活跃返回 {@code true}
     */
    boolean isActive(Long pageId);

    /**
     * 记录并校验一条 Yjs 更新（或首个 seed 全量状态）。
     *
     * @param ticket 票据
     * @param update base64 更新
     */
    void recordUpdate(DocWsTicket ticket, String update);

    /**
     * 应用领袖客户端提交的正文快照。
     *
     * @param ticket      票据
     * @param title       标题
     * @param contentJson TipTap 正文 JSON
     * @return 快照结果
     */
    DocCollabSnapshot snapshot(DocWsTicket ticket, String title, JsonNode contentJson);

    /**
     * 协作活跃标记过期时间。
     *
     * @return 秒
     */
    long activeTtlSeconds();

    /**
     * 协作快照建议间隔。
     *
     * @return 秒
     */
    int snapshotSeconds();
}
