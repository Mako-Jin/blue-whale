package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.RichTextSanitizer;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.IndexStat;
import com.bluewhale.forum.dto.SearchStatusVO;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索状态与索引回填。
 *
 * <p><b>为什么索引建在 {@code content_text} 而不是 {@code content}</b>：{@code content} 是
 * 富文本源码，搜 "p" / "div" / "href" 会命中几乎全部帖子；纯文本列既是索引载体，
 * 也是「重建」这个动作可以幂等重算的产物。</p>
 *
 * <p><b>为什么单字要回退 LIKE</b>：MySQL ngram 分词长度默认 2，长度 1 的关键词在索引里
 * 不存在倒排项，{@code MATCH} 恒空。这不是 bug，是分词器口径，只能在应用层回退。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** 检索引擎标识 */
    private static final String ENGINE = "mysql-ngram";

    /** 全文索引名（与 V7 迁移一致） */
    private static final String INDEX_NAME = "ft_fm_thread_search";

    /** ngram 分词长度缺省值（MySQL 默认 2） */
    private static final int DEFAULT_NGRAM_TOKEN_SIZE = 2;

    /** 单次重建条数上限，防止误操作一次全表重建把连接池占满 */
    private static final int MAX_REINDEX_PER_CALL = 5_000;

    private final ForumThreadMapper threadMapper;

    private final ForumProperties properties;

    /** ngram 分词长度缓存：数据库变量不会在运行期变，读一次即可 */
    private volatile Integer ngramTokenSize;

    @Override
    public SearchStatusVO status() {
        Long tenantId = TenantContext.requireId();
        IndexStat stat = threadMapper.selectIndexStat(tenantId);
        long total = stat == null || stat.getTotal() == null ? 0L : stat.getTotal();
        long indexed = stat == null || stat.getIndexed() == null ? 0L : stat.getIndexed();
        SearchStatusVO vo = new SearchStatusVO();
        vo.setEngine(ENGINE);
        vo.setIndexName(INDEX_NAME);
        vo.setNgramTokenSize(minKeywordLength());
        vo.setMinKeywordLength(minKeywordLength());
        // 单字一定走 LIKE：索引里没有单字倒排项
        vo.setLikeFallback(Boolean.TRUE);
        vo.setTotalThreads(total);
        vo.setIndexedThreads(indexed);
        vo.setPendingBackfill(Math.max(total - indexed, 0L));
        return vo;
    }

    @Override
    public int reindex(Integer limit) {
        Long tenantId = TenantContext.requireId();
        int batch = Math.max(1, properties.getSearchReindexBatch());
        int cap = limit == null || limit < 1 ? batch * 5 : Math.min(limit, MAX_REINDEX_PER_CALL);
        int done = 0;
        while (done < cap) {
            int fetch = Math.min(batch, cap - done);
            List<ForumThread> rows = threadMapper.selectPendingIndexRows(tenantId, fetch);
            if (rows.isEmpty()) {
                break;
            }
            for (ForumThread row : rows) {
                if (done >= cap) {
                    // 上限是硬约束：即使查询侧多返回了行，也不能越界处理
                    break;
                }
                // 复用摘要提取：去标签 + 压缩空白 + 截断，口径与列表摘要一致
                String plain = RichTextSanitizer.summarize(row.getContent(), properties.getSearchTextLength());
                threadMapper.updateContentText(row.getId(), plain);
                done++;
            }
            if (rows.size() < fetch) {
                break;
            }
        }
        if (done > 0) {
            log.info("论坛检索索引回填完成 tenantId={} rows={}", tenantId, done);
        }
        return done;
    }

    @Override
    public boolean useFulltext(String keyword) {
        if (keyword == null) {
            return false;
        }
        String trimmed = keyword.strip();
        // 长度按码点计：Java 的 length() 对 BMP 内汉字与字母都是 1，与 ngram 口径一致
        return trimmed.codePointCount(0, trimmed.length()) >= minKeywordLength();
    }

    @Override
    public int minKeywordLength() {
        int configured = properties.getSearchMinKeywordLength();
        if (configured > 0) {
            return configured;
        }
        return ngramTokenSize();
    }

    /**
     * 读数据库 ngram 分词长度（读不到时按 2 兜底：MariaDB 等无此变量）。
     *
     * @return 分词长度
     */
    private int ngramTokenSize() {
        Integer cached = ngramTokenSize;
        if (cached != null) {
            return cached;
        }
        int size = DEFAULT_NGRAM_TOKEN_SIZE;
        try {
            Integer value = threadMapper.selectNgramTokenSize();
            if (value != null && value > 0) {
                size = value;
            }
        } catch (RuntimeException e) {
            // 换库/未装 ngram 时不该让搜索接口 500：按默认 2 走，长词仍能命中（退化为 LIKE）
            log.warn("读取 @@ngram_token_size 失败，按 {} 处理", DEFAULT_NGRAM_TOKEN_SIZE, e);
        }
        ngramTokenSize = size;
        return size;
    }
}
