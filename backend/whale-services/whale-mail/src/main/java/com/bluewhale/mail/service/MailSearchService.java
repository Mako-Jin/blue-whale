package com.bluewhale.mail.service;

import com.bluewhale.mail.dto.MailSearchQuery;
import com.bluewhale.mail.entity.MailMessage;

import java.util.List;

/**
 * 邮件全文检索契约。
 *
 * <p>两套实现按 {@code whale.mail.search.engine} 装配：MySQL ngram 全文索引（默认）
 * 与 Lucene 本地倒排索引。调用方只依赖本接口，所以切引擎不需要改业务代码。</p>
 *
 * <p>约定：{@link #searchIds} 返回的是**按相关度降序**的邮件ID，不是邮件实体。
 * 由调用方按 ID 顺序重排实体——这样分页可以在内存里做，不必让引擎理解分页语义；
 * 命中过多时由 {@code limit} 截断。</p>
 *
 * @author BlueWhale
 */
public interface MailSearchService {

    /**
     * 检索命中的邮件ID（相关度降序）。
     *
     * @param query 检索条件
     * @return 邮件ID列表；无命中或检索词过短返回空列表
     */
    List<Long> searchIds(MailSearchQuery query);

    /**
     * 写入或更新单封邮件的索引。
     *
     * <p>MySQL 实现为空操作（索引由数据库自己维护）；Lucene 实现需要在同步入库、
     * 正文回填、状态变更后调用，否则索引与库会漂移。</p>
     *
     * @param message 邮件实体
     */
    void index(MailMessage message);

    /**
     * 删除单封邮件的索引。
     *
     * @param messageId 邮件ID
     */
    void remove(Long messageId);

    /**
     * 重建指定账号的索引（MySQL 实现为空操作，返回 0）。
     *
     * @param accountId 邮箱账号ID
     * @return 重建的文档数
     */
    int rebuild(Long accountId);

    /**
     * 当前引擎标识，用于接口回显与排障。
     *
     * @return {@code mysql} 或 {@code lucene}
     */
    String engine();

    /**
     * 当前引擎是否支持独立索引（决定前端是否展示「重建索引」）。
     *
     * @return 需要独立索引返回 {@code true}
     */
    default boolean externalIndex() {
        return false;
    }
}
