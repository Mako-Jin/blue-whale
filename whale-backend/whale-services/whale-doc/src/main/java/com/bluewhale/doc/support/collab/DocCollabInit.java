package com.bluewhale.doc.support.collab;

import java.util.List;

/**
 * 协作连接初始化数据（M2）。
 *
 * <p>{@code updates} 为 Redis 更新日志中的历史 Yjs 更新（base64）；日志为空时客户端应以
 * {@code contentJson} 初始化 Y.Doc 并回传 {@code seed}。</p>
 *
 * @param pageId      页面ID
 * @param title       页面标题
 * @param revision    当前服务端 revision
 * @param status      页面状态
 * @param contentJson 当前正文 JSON；无需初始化正文时为 {@code null}
 * @param updates     Yjs 更新日志（按追加顺序）
 * @author BlueWhale
 */
public record DocCollabInit(Long pageId, String title, Long revision, String status,
                            String contentJson, List<String> updates) {
}
