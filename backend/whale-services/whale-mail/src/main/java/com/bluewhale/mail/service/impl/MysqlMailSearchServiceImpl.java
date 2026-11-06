package com.bluewhale.mail.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailSearchQuery;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.support.MailSearchText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 MySQL ngram 全文索引的检索实现（默认引擎）。
 *
 * <p>选它做默认的原因：索引与数据在同一个事务域内，不存在「库里改了、索引没跟上」的
 * 漂移窗口，也不需要额外的目录持久化与多实例同步。代价是相关度排序只有 BM25 一档，
 * 权重调不动——真正的排序需求出现时换 Lucene 实现即可。</p>
 *
 * <p>写入侧是空实现：{@code mail_message} 的插入/更新由 MySQL 自己维护全文索引项，
 * 应用层再写一遍反而会重复。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.mail.search", name = "engine", havingValue = "mysql", matchIfMissing = true)
public class MysqlMailSearchServiceImpl implements MailSearchService {

    /** 引擎标识 */
    private static final String ENGINE = "mysql";

    /** 邮件索引 Mapper（全文检索走自定义 SELECT） */
    private final MailMessageMapper messageMapper;

    private final MailProperties properties;

    @Override
    public List<Long> searchIds(MailSearchQuery query) {
        if (query == null || query.accountId() == null) {
            return List.of();
        }
        String keyword = MailSearchText.normalize(query.keyword());
        if (MailSearchText.isTooShort(keyword)) {
            return List.of();
        }
        String expr = MailSearchText.toBooleanExpr(keyword);
        if (expr == null) {
            return List.of();
        }
        Long tenant = TenantContext.get();
        long tenantId = tenant == null ? TenantContext.DEFAULT_TENANT_ID : tenant;
        int limit = Math.min(Math.max(query.limit(), 1), properties.getSearch().getMaxHits());
        try {
            return messageMapper.searchFullText(tenantId, query.accountId(), query.folderId(), expr, limit);
        } catch (Exception e) {
            // 索引缺失（迁移未执行）或 SQL 方言不兼容时不能把列表接口打成 500：
            // 搜索是增强能力，失败就让上层退回 LIKE，结果是「慢一点」而不是「搜不了」
            log.warn("全文检索失败，回退模糊匹配: accountId={}, keyword={}, reason={}",
                    query.accountId(), keyword, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void index(MailMessage message) {
        // 索引随表维护，无需应用层写入
    }

    @Override
    public void remove(Long messageId) {
        // 逻辑删除由 deleted 列表达，MySQL 全文索引会自动排除已删除行
    }

    @Override
    public int rebuild(Long accountId) {
        // 无需重建：OPTIMIZE TABLE 由运维在碎片化时安排，不在请求链路里做
        return 0;
    }

    @Override
    public String engine() {
        return ENGINE;
    }
}
