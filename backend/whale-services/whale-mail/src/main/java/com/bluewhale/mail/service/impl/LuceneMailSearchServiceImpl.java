package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailSearchQuery;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.support.MailSearchText;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Lucene 本地倒排索引的检索实现（{@code whale.mail.search.engine=lucene} 时装配）。
 *
 * <p>相比 MySQL ngram 全文索引，这里能做的只有两件 DB 做不到的事：跨字段的分数加权
 * （主题 3.0 &gt; 发件人 2.0 &gt; 摘要 1.4 &gt; 正文 1.0）与更细的中文分词。代价是索引
 * 与库要自己保持一致，且索引目录必须落在持久卷上。</p>
 *
 * <p><b>为什么文档里不写租户字段：</b>同步与定时任务线程没有租户上下文，写进去的
 * 租户值会是默认值，多租户下会导致 A 租户搜到 B 租户的邮件。账号ID 全局唯一且已经
 * 与租户强绑定（调用方检索前必须校验账号归属），只按账号过滤既够用又不会有这个坑。</p>
 *
 * <p><b>写入节奏：</b>{@code addDocument} 只写内存段，不逐条提交——单封邮件一次 commit
 * 会让同步一万封时产生一万次 fsync。提交交给每 30 秒的定时刷新与停机时的关停流程，
 * 搜索前用 {@code maybeRefreshBlocking} 让内存段对检索可见。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.mail.search", name = "engine", havingValue = "lucene")
public class LuceneMailSearchServiceImpl implements MailSearchService, DisposableBean {

    /** 单次查询最多使用的分词数 */
    private static final int MAX_QUERY_TOKENS = 24;

    /** 索引字段：邮件ID（唯一键，用于 upsert 与删除） */
    private static final String F_ID = "id";

    /** 索引字段：账号ID（唯一的隔离维度，见类注释） */
    private static final String F_ACCOUNT = "account";

    /** 索引字段：文件夹ID（可选过滤） */
    private static final String F_FOLDER = "folder";

    /** 索引字段：主题（权重最高） */
    private static final String F_SUBJECT = "subject";

    /** 索引字段：发件人地址与显示名 */
    private static final String F_FROM = "from";

    /** 索引字段：纯文本摘要 */
    private static final String F_PREVIEW = "preview";

    /** 索引字段：纯文本正文（懒加载，多数行为空） */
    private static final String F_BODY = "body";

    /** 未提交写入累积到该条数时主动提交，避免长时间只靠定时刷新兜底 */
    private static final int COMMIT_EVERY = 200;

    private final MailProperties properties;

    private final MailMessageMapper messageMapper;

    private final ReentrantLock writeLock = new ReentrantLock();

    private Directory directory;

    private Analyzer analyzer;

    private IndexWriter writer;

    private SearcherManager searcherManager;

    /** 索引是否可用：初始化失败时降级为「检索返回空」，由调用方回退 LIKE */
    private volatile boolean available;

    /** 未提交的写入计数 */
    private volatile int pendingWrites;

    /**
     * 初始化索引目录、写入器与近实时检索器。
     *
     * <p>失败不阻断启动：索引是检索的加速层，目录不可写时邮箱本身的收发不受影响，
     * 搜索退回 LIKE 即可。这一点与知识库索引（失败即启动失败）不同——那里索引就是产品本体。</p>
     */
    @PostConstruct
    public void init() {
        try {
            Path indexDir = Path.of(properties.getSearch().getIndexDir()).toAbsolutePath();
            Files.createDirectories(indexDir);
            analyzer = new SmartChineseAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            directory = FSDirectory.open(indexDir);
            writer = new IndexWriter(directory, config);
            writer.commit();
            searcherManager = new SearcherManager(writer, new SearcherFactory());
            available = true;
            log.info("邮件索引初始化完成: dir={}, docs={}", indexDir, count());
        } catch (IOException e) {
            available = false;
            log.error("邮件索引初始化失败，检索将退回模糊匹配: reason={}", e.getMessage());
        }
    }

    @Override
    public List<Long> searchIds(MailSearchQuery query) {
        if (!available || query == null || query.accountId() == null) {
            return List.of();
        }
        String keyword = MailSearchText.normalize(query.keyword());
        if (MailSearchText.isTooShort(keyword)) {
            return List.of();
        }
        List<String> tokens = tokenize(keyword);
        if (tokens.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(Math.max(query.limit(), 1), properties.getSearch().getMaxHits());
        refresh();
        IndexSearcher searcher = acquire();
        try {
            Query luceneQuery = buildQuery(tokens, query.accountId(), query.folderId());
            TopDocs topDocs = searcher.search(luceneQuery, limit);
            StoredFields storedFields = searcher.storedFields();
            List<Long> ids = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                String id = storedFields.document(scoreDoc.doc).get(F_ID);
                if (id != null) {
                    ids.add(Long.valueOf(id));
                }
            }
            return ids;
        } catch (IOException e) {
            log.warn("Lucene 检索失败，回退模糊匹配: keyword={}, reason={}", keyword, e.getMessage());
            return List.of();
        } finally {
            release(searcher);
        }
    }

    @Override
    public void index(MailMessage message) {
        if (!available || message == null || message.getId() == null) {
            return;
        }
        writeLock.lock();
        try {
            writer.updateDocument(new Term(F_ID, String.valueOf(message.getId())), toDocument(message));
            pendingWrites++;
            if (pendingWrites >= COMMIT_EVERY) {
                writer.commit();
                pendingWrites = 0;
            }
        } catch (IOException e) {
            log.warn("邮件索引写入失败: id={}, reason={}", message.getId(), e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(Long messageId) {
        if (!available || messageId == null) {
            return;
        }
        writeLock.lock();
        try {
            writer.deleteDocuments(new Term(F_ID, String.valueOf(messageId)));
            pendingWrites++;
        } catch (IOException e) {
            log.warn("邮件索引删除失败: id={}, reason={}", messageId, e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int rebuild(Long accountId) {
        if (!available || accountId == null) {
            return 0;
        }
        int total = 0;
        long lastId = 0L;
        int batch = Math.max(properties.getSync().getBatchSize(), 100);
        writeLock.lock();
        try {
            writer.deleteDocuments(new Term(F_ACCOUNT, String.valueOf(accountId)));
            while (true) {
                List<MailMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                        .eq(MailMessage::getAccountId, accountId)
                        .gt(MailMessage::getId, lastId)
                        .orderByAsc(MailMessage::getId)
                        .last("LIMIT " + batch));
                if (rows.isEmpty()) {
                    break;
                }
                for (MailMessage row : rows) {
                    writer.updateDocument(new Term(F_ID, String.valueOf(row.getId())), toDocument(row));
                    lastId = row.getId();
                    total++;
                }
            }
            writer.commit();
            pendingWrites = 0;
        } catch (IOException e) {
            log.warn("邮件索引重建失败: accountId={}, 已写入={}, reason={}", accountId, total, e.getMessage());
        } finally {
            writeLock.unlock();
        }
        refresh();
        log.info("邮件索引重建完成: accountId={}, docs={}", accountId, total);
        return total;
    }

    @Override
    public String engine() {
        return "lucene";
    }

    @Override
    public boolean externalIndex() {
        return true;
    }

    /**
     * 定时提交未落盘的索引变更。
     *
     * <p>提交频率与同步间隔解耦：同步可能几分钟才跑一次，而索引提交只要不丢太多就行，
     * 30 秒一次既控制了 fsync 次数，也让进程被强杀时最多丢半分钟的新增邮件索引。</p>
     */
    @Scheduled(fixedDelayString = "${whale.mail.search.commit-interval-millis:30000}", initialDelay = 30_000)
    public void flush() {
        if (!available || pendingWrites <= 0) {
            return;
        }
        writeLock.lock();
        try {
            writer.commit();
            pendingWrites = 0;
        } catch (IOException e) {
            log.warn("邮件索引提交失败: reason={}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 构造 Lucene 查询：词条之间 AND，每个词条在四个字段间取最高分。
     *
     * @param tokens 分词结果
     * @param accountId 账号ID（必须过滤）
     * @param folderId 文件夹ID（{@code null} 表示跨文件夹）
     * @return Lucene 查询
     */
    private Query buildQuery(List<String> tokens, Long accountId, Long folderId) {
        BooleanQuery.Builder root = new BooleanQuery.Builder();
        for (String token : tokens) {
            List<Query> perField = new ArrayList<>(4);
            perField.add(new BoostQuery(new TermQuery(new Term(F_SUBJECT, token)), 3.0f));
            perField.add(new BoostQuery(new TermQuery(new Term(F_FROM, token)), 2.0f));
            perField.add(new BoostQuery(new TermQuery(new Term(F_PREVIEW, token)), 1.4f));
            perField.add(new BoostQuery(new TermQuery(new Term(F_BODY, token)), 1.0f));
            root.add(new DisjunctionMaxQuery(perField, 0.1f), BooleanClause.Occur.MUST);
        }
        root.add(new TermQuery(new Term(F_ACCOUNT, String.valueOf(accountId))), BooleanClause.Occur.FILTER);
        if (folderId != null) {
            root.add(new TermQuery(new Term(F_FOLDER, String.valueOf(folderId))), BooleanClause.Occur.FILTER);
        }
        return root.build();
    }

    /**
     * 组装索引文档。
     *
     * @param message 邮件实体
     * @return Lucene 文档
     */
    private Document toDocument(MailMessage message) {
        Document document = new Document();
        document.add(new StringField(F_ID, String.valueOf(message.getId()), Field.Store.YES));
        document.add(new StringField(F_ACCOUNT, String.valueOf(message.getAccountId()), Field.Store.NO));
        if (message.getFolderId() != null) {
            document.add(new StringField(F_FOLDER, String.valueOf(message.getFolderId()), Field.Store.NO));
        }
        document.add(new TextField(F_SUBJECT, nullToEmpty(message.getSubject()), Field.Store.NO));
        document.add(new TextField(F_FROM, fromText(message), Field.Store.NO));
        document.add(new TextField(F_PREVIEW, nullToEmpty(message.getPreview()), Field.Store.NO));
        document.add(new TextField(F_BODY, nullToEmpty(message.getBodyText()), Field.Store.NO));
        return document;
    }

    /**
     * 拼出发件人检索文本（地址与显示名都参与匹配）。
     *
     * @param message 邮件实体
     * @return 发件人文本
     */
    private String fromText(MailMessage message) {
        String addr = nullToEmpty(message.getFromAddr());
        String name = nullToEmpty(message.getFromName());
        return name.isEmpty() ? addr : addr + " " + name;
    }

    /**
     * 用同一种分析器切分检索词（索引与查询必须同源，否则词项对不上）。
     *
     * @param keyword 归一化后的检索词
     * @return 词项列表（去重、限量、滤掉单字噪声）
     */
    private List<String> tokenize(String keyword) {
        Set<String> tokens = new LinkedHashSet<>();
        try (TokenStream stream = analyzer.tokenStream(F_BODY, keyword)) {
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && tokens.size() < MAX_QUERY_TOKENS) {
                String token = attribute.toString().trim();
                if (token.isEmpty() || isSingleHan(token)) {
                    continue;
                }
                tokens.add(token);
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException("检索词分词失败", e);
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 判断是否为单个汉字（单字在中文检索里基本是噪声，且会被大量命中）。
     *
     * @param token 词项
     * @return 单汉字返回 {@code true}
     */
    private boolean isSingleHan(String token) {
        return token.length() == 1 && Character.UnicodeScript.of(token.charAt(0)) == Character.UnicodeScript.HAN;
    }

    /**
     * 让内存段对检索可见。
     */
    private void refresh() {
        try {
            searcherManager.maybeRefreshBlocking();
        } catch (IOException e) {
            log.warn("邮件索引刷新失败: reason={}", e.getMessage());
        }
    }

    /**
     * 获取检索器（调用方必须归还）。
     *
     * @return 检索器；不可用返回 {@code null}
     */
    private IndexSearcher acquire() {
        try {
            return searcherManager.acquire();
        } catch (IOException e) {
            throw new UncheckedIOException("获取邮件索引检索器失败", e);
        }
    }

    /**
     * 归还检索器。
     *
     * @param searcher 检索器（可为 {@code null}）
     */
    private void release(IndexSearcher searcher) {
        if (searcher == null) {
            return;
        }
        try {
            searcherManager.release(searcher);
        } catch (IOException e) {
            log.warn("释放邮件索引检索器失败", e);
        }
    }

    /**
     * 当前索引文档数。
     *
     * @return 文档数；索引不可用返回 0
     */
    private int count() {
        if (!available) {
            return 0;
        }
        IndexSearcher searcher = acquire();
        try {
            return searcher.getIndexReader().numDocs();
        } finally {
            release(searcher);
        }
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void destroy() throws Exception {
        if (writer != null) {
            writeLock.lock();
            try {
                writer.commit();
            } finally {
                writeLock.unlock();
            }
        }
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (analyzer != null) {
            analyzer.close();
        }
        if (directory != null) {
            directory.close();
        }
    }
}
