package com.bluewhale.agent.support.index;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.ingest.Chunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lucene 索引服务：负责索引生命周期、写入/删除、BM25 检索与索引统计。
 *
 * <p>双分析器：{@code content}/{@code title} 走 {@link SmartChineseAnalyzer} 处理中文；
 * {@code content_code}/{@code title_code}/{@code path_text} 走 {@link StandardAnalyzer}
 * 保留 camelCase / snake_case 标识符。索引目录由 {@code whale.agent.index-dir} 配置。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class AgentIndexService implements DisposableBean {

    /** 单次查询最多使用的分词数，避免超长问题拖慢检索 */
    private static final int MAX_QUERY_TOKENS = 24;

    private final AgentProperties properties;

    private final ReentrantLock writeLock = new ReentrantLock();

    private Directory directory;

    private Analyzer analyzer;

    private IndexWriter writer;

    private SearcherManager searcherManager;

    private volatile String status = "EMPTY";

    private volatile LocalDateTime lastBuildAt;

    private volatile Long buildMillis;

    /**
     * 构造索引服务。
     *
     * @param properties Agent 配置
     */
    public AgentIndexService(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化索引目录、写入器与近实时检索器。
     */
    @PostConstruct
    public void init() {
        try {
            Path indexDir = properties.resolveIndexDir();
            Files.createDirectories(indexDir);
            SmartChineseAnalyzer smart = new SmartChineseAnalyzer();
            StandardAnalyzer code = new StandardAnalyzer();
            Map<String, Analyzer> perField = new HashMap<>();
            perField.put(AgentIndexFields.CONTENT_CODE, code);
            perField.put(AgentIndexFields.TITLE_CODE, code);
            perField.put(AgentIndexFields.PATH_TEXT, code);
            analyzer = new PerFieldAnalyzerWrapper(smart, perField);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            directory = FSDirectory.open(indexDir);
            writer = new IndexWriter(directory, config);
            writer.commit();
            searcherManager = new SearcherManager(writer, new SearcherFactory());
            status = count() > 0 ? "READY" : "EMPTY";
            log.info("知识索引初始化完成：dir={}，块数={}", indexDir, count());
        } catch (IOException e) {
            throw new IllegalStateException("知识索引初始化失败", e);
        }
    }

    /**
     * 清空索引全部文档（全量重建前调用）。
     */
    public void deleteAll() {
        writeLock.lock();
        try {
            writer.deleteAll();
        } catch (IOException e) {
            throw new UncheckedIOException("清空索引失败", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 按路径删除文档（增量更新文件前调用）。
     *
     * @param paths 仓库相对路径集合
     */
    public void deletePaths(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        writeLock.lock();
        try {
            for (String path : paths) {
                writer.deleteDocuments(new Term(AgentIndexFields.PATH, path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("删除索引文档失败", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 写入单个文件的全部知识块。
     *
     * @param path 仓库相对路径
     * @param hash 文件内容哈希
     * @param chunks 知识块列表
     */
    public void addFileChunks(String path, String hash, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        writeLock.lock();
        try {
            int seq = 0;
            for (Chunk chunk : chunks) {
                writer.addDocument(toDocument(chunk, hash, seq++));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("写入索引失败: " + path, e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 提交写入并刷新近实时检索器。
     */
    public void commitAndRefresh() {
        writeLock.lock();
        try {
            writer.commit();
            searcherManager.maybeRefreshBlocking();
            lastBuildAt = LocalDateTime.now();
            status = count() > 0 ? "READY" : "EMPTY";
        } catch (IOException e) {
            throw new UncheckedIOException("提交索引失败", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 读取索引中已有的「路径 → 哈希」映射（增量扫描依据）。
     *
     * @return 路径哈希映射
     */
    public Map<String, String> listFileHashes() {
        IndexSearcher searcher = acquireSearcher();
        try {
            Map<String, String> result = new HashMap<>();
            for (LeafReaderContext context : searcher.getIndexReader().leaves()) {
                StoredFields storedFields = context.reader().storedFields();
                int maxDoc = context.reader().maxDoc();
                for (int docId = 0; docId < maxDoc; docId++) {
                    Document document = storedFields.document(docId);
                    String path = document.get(AgentIndexFields.PATH);
                    String hash = document.get(AgentIndexFields.HASH);
                    if (path != null && hash != null) {
                        result.put(path, hash);
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("读取索引哈希失败", e);
        } finally {
            releaseSearcher(searcher);
        }
    }

    /**
     * BM25 检索。
     *
     * @param question 用户问题
     * @param topK 返回条数
     * @param kinds 知识类型过滤（空表示不过滤）
     * @return 命中结果（按分数降序）
     */
    public List<SearchHit> search(String question, int topK, Set<String> kinds) {
        if (!StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        IndexSearcher searcher = acquireSearcher();
        try {
            Query query = buildQuery(question, kinds);
            TopDocs topDocs = searcher.search(query, topK);
            StoredFields storedFields = searcher.storedFields();
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                hits.add(toHit(storedFields.document(scoreDoc.doc), scoreDoc.score));
            }
            return hits;
        } catch (IOException e) {
            throw new UncheckedIOException("知识检索失败", e);
        } finally {
            releaseSearcher(searcher);
        }
    }

    /**
     * 当前索引块数。
     *
     * @return 文档数
     */
    public int count() {
        IndexSearcher searcher = acquireSearcher();
        try {
            return searcher.getIndexReader().numDocs();
        } finally {
            releaseSearcher(searcher);
        }
    }

    /**
     * 构建检索查询：中文分词 + 代码分词双通道，字段加权。
     *
     * @param question 用户问题
     * @param kinds 类型过滤
     * @return Lucene 查询
     */
    private Query buildQuery(String question, Set<String> kinds) {
        List<String> cnTokens = tokenize(AgentIndexFields.CONTENT, question, false);
        List<String> codeTokens = new ArrayList<>(tokenize(AgentIndexFields.CONTENT_CODE, question, true));
        for (String alias : QueryAliasExpander.expand(question)) {
            if (!codeTokens.contains(alias)) {
                codeTokens.add(alias);
            }
        }
        if (cnTokens.isEmpty() && codeTokens.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.setMinimumNumberShouldMatch(1);
        for (String token : cnTokens) {
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.TITLE, token)), 2.5f),
                    BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.CONTENT, token)), 1.0f),
                    BooleanClause.Occur.SHOULD);
        }
        for (String token : codeTokens) {
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.TITLE_CODE, token)), 2.5f),
                    BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.CONTENT_CODE, token)), 1.2f),
                    BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.PATH_TEXT, token)), 1.8f),
                    BooleanClause.Occur.SHOULD);
        }
        if (kinds != null && !kinds.isEmpty()) {
            BooleanQuery.Builder filter = new BooleanQuery.Builder();
            for (String kind : kinds) {
                filter.add(new TermQuery(new Term(AgentIndexFields.KIND, kind)), BooleanClause.Occur.SHOULD);
            }
            builder.add(filter.build(), BooleanClause.Occur.FILTER);
        }
        return builder.build();
    }

    /**
     * 分词（去重、限长）。
     *
     * @param field 字段名（决定使用的分析器）
     * @param text 待分词文本
     * @param codeMode 代码模式：过滤单字中文噪声
     * @return 词项列表
     */
    private List<String> tokenize(String field, String text, boolean codeMode) {
        Set<String> tokens = new LinkedHashSet<>();
        try (TokenStream stream = analyzer.tokenStream(field, text)) {
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && tokens.size() < MAX_QUERY_TOKENS) {
                String token = attribute.toString().trim();
                if (token.isEmpty() || (codeMode && isSingleHan(token))) {
                    continue;
                }
                tokens.add(token);
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException("查询分词失败", e);
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 判断是否单个汉字（代码字段检索时排除，避免噪声）。
     *
     * @param token 词项
     * @return 是单汉字返回 {@code true}
     */
    private boolean isSingleHan(String token) {
        return token.length() == 1 && Character.UnicodeScript.of(token.charAt(0)) == Character.UnicodeScript.HAN;
    }

    /**
     * 构建 Lucene 文档。
     *
     * @param chunk 知识块
     * @param hash 文件哈希
     * @param seq 块序号
     * @return Lucene 文档
     */
    private Document toDocument(Chunk chunk, String hash, int seq) {
        String content = nullToEmpty(chunk.getContent());
        String title = nullToEmpty(chunk.getTitle());
        Document document = new Document();
        document.add(new StringField(AgentIndexFields.ID,
                chunk.getKind() + ":" + chunk.getPath() + ":" + hash + ":" + seq, Field.Store.YES));
        document.add(new StringField(AgentIndexFields.KIND, nullToEmpty(chunk.getKind()), Field.Store.YES));
        document.add(new StringField(AgentIndexFields.MODULE, nullToEmpty(chunk.getModule()), Field.Store.YES));
        document.add(new StringField(AgentIndexFields.PATH, chunk.getPath(), Field.Store.YES));
        document.add(new TextField(AgentIndexFields.PATH_TEXT, chunk.getPath(), Field.Store.NO));
        document.add(new TextField(AgentIndexFields.TITLE, title, Field.Store.YES));
        document.add(new TextField(AgentIndexFields.TITLE_CODE, title, Field.Store.NO));
        document.add(new TextField(AgentIndexFields.CONTENT, content, Field.Store.YES));
        document.add(new TextField(AgentIndexFields.CONTENT_CODE, content, Field.Store.NO));
        document.add(new StoredField(AgentIndexFields.START_LINE, chunk.getStartLine()));
        document.add(new StoredField(AgentIndexFields.END_LINE, chunk.getEndLine()));
        document.add(new StringField(AgentIndexFields.WAVE, nullToEmpty(chunk.getWave()), Field.Store.YES));
        document.add(new StoredField(AgentIndexFields.HASH, hash));
        document.add(new StringField(AgentIndexFields.VISIBILITY, "public", Field.Store.YES));
        return document;
    }

    /**
     * 转换为检索命中结果。
     *
     * @param document Lucene 文档
     * @param score BM25 分数
     * @return 命中结果
     */
    private SearchHit toHit(Document document, float score) {
        return SearchHit.builder()
                .id(document.get(AgentIndexFields.ID))
                .kind(nullToEmpty(document.get(AgentIndexFields.KIND)))
                .module(nullToEmpty(document.get(AgentIndexFields.MODULE)))
                .path(nullToEmpty(document.get(AgentIndexFields.PATH)))
                .title(nullToEmpty(document.get(AgentIndexFields.TITLE)))
                .content(nullToEmpty(document.get(AgentIndexFields.CONTENT)))
                .startLine(intValue(document.get(AgentIndexFields.START_LINE)))
                .endLine(intValue(document.get(AgentIndexFields.END_LINE)))
                .wave(nullToEmpty(document.get(AgentIndexFields.WAVE)))
                .score(score)
                .build();
    }

    /**
     * 读取 StoredField 中的数值。
     *
     * @param value 字符串值
     * @return 数值；缺失或非法返回 0
     */
    private int intValue(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取当前索引统计。
     *
     * @param fileCount 文件数（由调用方统计）
     * @return 索引统计
     */
    public IndexStats stats(int fileCount) {
        return IndexStats.builder()
                .fileCount(fileCount)
                .chunkCount(count())
                .lastBuildAt(lastBuildAt)
                .status(status)
                .buildMillis(buildMillis)
                .build();
    }

    /**
     * 标记构建开始。
     */
    public void markBuildStart() {
        status = "BUILDING";
    }

    /**
     * 标记构建成功。
     *
     * @param millis 构建耗时（毫秒）
     */
    public void markBuildSuccess(long millis) {
        status = count() > 0 ? "READY" : "EMPTY";
        lastBuildAt = LocalDateTime.now();
        buildMillis = millis;
    }

    /**
     * 标记构建失败。
     */
    public void markBuildFailed() {
        status = "FAILED";
    }

    /**
     * 获取当前状态文本。
     *
     * @return READY / BUILDING / FAILED / EMPTY
     */
    public String status() {
        return status;
    }

    /**
     * 获取最近构建时间。
     *
     * @return 最近构建时间；未构建返回 {@code null}
     */
    public LocalDateTime lastBuildAt() {
        return lastBuildAt;
    }

    /**
     * 获取最近构建耗时。
     *
     * @return 耗时毫秒；未构建返回 {@code null}
     */
    public Long buildMillis() {
        return buildMillis;
    }

    /**
     * 获取检索器（调用方必须 release）。
     *
     * @return 索引检索器
     */
    private IndexSearcher acquireSearcher() {
        try {
            return searcherManager.acquire();
        } catch (IOException e) {
            throw new UncheckedIOException("获取索引检索器失败", e);
        }
    }

    /**
     * 归还检索器。
     *
     * @param searcher 检索器
     */
    private void releaseSearcher(IndexSearcher searcher) {
        try {
            searcherManager.release(searcher);
        } catch (IOException e) {
            log.warn("释放索引检索器失败", e);
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
