package com.bluewhale.agent.support.index;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.service.EmbeddingService;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.agent.support.knowledge.KnowledgePermissionContext;
import com.bluewhale.common.tenant.TenantContext;
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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
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
import java.util.Collections;
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

    /** 单块参与向量化的最大字符数（标题 + 正文截断） */
    private static final int MAX_EMBED_CHARS = 2000;

    /** 平台级知识（代码/文档/平台事实）的租户标记，对所有租户可见 */
    private static final long PLATFORM_TENANT_ID = 0L;

    private final AgentProperties properties;

    private final EmbeddingService embeddingService;

    private final ReentrantLock writeLock = new ReentrantLock();

    private Directory directory;

    private Analyzer analyzer;

    private IndexWriter writer;

    private SearcherManager searcherManager;

    /** 索引内实际向量维度（0 表示无向量） */
    private volatile int indexedDimension;

    /** 索引构建时的 embedding 签名 */
    private volatile String indexedSignature = "none";

    private volatile String status = "EMPTY";

    private volatile LocalDateTime lastBuildAt;

    private volatile Long buildMillis;

    /**
     * 构造索引服务。
     *
     * @param properties       Agent 配置
     * @param embeddingService 向量化服务
     */
    public AgentIndexService(AgentProperties properties, EmbeddingService embeddingService) {
        this.properties = properties;
        this.embeddingService = embeddingService;
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
            refreshIndexedMeta();
            status = count() > 0 ? "READY" : "EMPTY";
            log.info("知识索引初始化完成：dir={}，块数={}，向量维度={}，embedding={}",
                    indexDir, count(), indexedDimension, indexedSignature);
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
        addFileChunks(path, hash, chunks, null, null, PLATFORM_TENANT_ID);
    }

    /**
     * 写入单个文件的全部知识块（业务文件知识库）。
     *
     * @param path 知识块路径（{@code file:<id>}）
     * @param hash 文件内容哈希
     * @param chunks 知识块列表
     * @param fileId 文件服务文件ID（可为 {@code null}）
     * @param permissionCode 所需权限码（可为 {@code null}）
     */
    public void addFileChunks(String path, String hash, List<Chunk> chunks, Long fileId, String permissionCode) {
        Long tenant = TenantContext.get();
        addFileChunks(path, hash, chunks, fileId, permissionCode,
                tenant == null ? TenantContext.DEFAULT_TENANT_ID : tenant);
    }

    /**
     * 写入单个文件的全部知识块（带归属人，租户取当前上下文）。
     *
     * @param path 知识块路径（{@code file:<id>}）
     * @param hash 文件内容哈希
     * @param chunks 知识块列表
     * @param fileId 文件服务文件ID（可为 {@code null}）
     * @param permissionCode 所需权限码（可为 {@code null}）
     * @param ownerId 归属人ID（上传者；{@code null} 表示租户内公共知识）
     */
    public void addFileChunksWithOwner(String path, String hash, List<Chunk> chunks, Long fileId,
                                       String permissionCode, Long ownerId) {
        Long tenant = TenantContext.get();
        writeChunks(path, hash, chunks, fileId, null, permissionCode,
                tenant == null ? TenantContext.DEFAULT_TENANT_ID : tenant, ownerId);
    }

    /**
     * 写入单个文件的全部知识块（显式指定租户）。
     *
     * @param path 知识块路径
     * @param hash 文件内容哈希
     * @param chunks 知识块列表
     * @param fileId 文件服务文件ID（可为 {@code null}）
     * @param permissionCode 所需权限码（可为 {@code null}）
     * @param tenantId 租户ID（{@code 0} 表示平台级知识）
     */
    public void addFileChunks(String path, String hash, List<Chunk> chunks, Long fileId, String permissionCode,
                              long tenantId) {
        writeChunks(path, hash, chunks, fileId, null, permissionCode, tenantId, null);
    }

    /**
     * 写入文档页面知识块（whale-doc 知识源）。
     *
     * @param path           知识块路径（{@code docpage:<id>}）
     * @param hash           正文哈希
     * @param chunks         知识块列表
     * @param meta           页面元数据（页面ID、空间ID、页面地址）
     * @param permissionCode 所需权限码（文档读权限）
     * @param tenantId       租户ID
     */
    public void addDocChunks(String path, String hash, List<Chunk> chunks, DocIndexMeta meta,
                             String permissionCode, long tenantId) {
        writeChunks(path, hash, chunks, null, meta, permissionCode, tenantId, null);
    }

    /**
     * 写入知识块（文件/文档共用）。
     *
     * @param path           知识块路径
     * @param hash           内容哈希
     * @param chunks         知识块列表
     * @param fileId         文件服务文件ID（可空）
     * @param docMeta        文档页面元数据（可空）
     * @param permissionCode 所需权限码（可空）
     * @param tenantId       租户ID
     * @param ownerId        归属人ID（可空，仅文件知识写入）
     */
    private void writeChunks(String path, String hash, List<Chunk> chunks, Long fileId,
                             DocIndexMeta docMeta, String permissionCode, long tenantId, Long ownerId) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = embedChunks(chunks);
        String signature = embeddingService.signature();
        writeLock.lock();
        try {
            int seq = 0;
            for (int i = 0; i < chunks.size(); i++) {
                writer.addDocument(toDocument(chunks.get(i), hash, seq++, vectors.get(i), signature,
                        fileId, docMeta, permissionCode, tenantId, ownerId));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("写入索引失败: " + path, e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 按知识类型删除当前租户的全部文档（文件知识库全量重建前调用）。
     *
     * <p>默认租户同时清理历史遗留的无租户字段文档，保证升级前的文件知识被重建。</p>
     *
     * @param kind 知识类型（如 {@code file}）
     */
    public void deleteKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            return;
        }
        writeLock.lock();
        try {
            writer.deleteDocuments(tenantScopedKindQuery(kind));
        } catch (IOException e) {
            throw new UncheckedIOException("按类型删除索引失败: " + kind, e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 构建「类型 + 租户」删除查询：默认租户兼容无租户字段的历史文档。
     *
     * @param kind 知识类型
     * @return 删除查询
     */
    private Query tenantScopedKindQuery(String kind) {
        Long tenant = TenantContext.get();
        long tenantId = tenant == null ? TenantContext.DEFAULT_TENANT_ID : tenant;
        BooleanQuery.Builder tenantQuery = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(AgentIndexFields.TENANT, String.valueOf(tenantId))),
                        BooleanClause.Occur.SHOULD);
        if (tenantId == TenantContext.DEFAULT_TENANT_ID) {
            tenantQuery.add(missingTenantQuery(), BooleanClause.Occur.SHOULD);
        }
        tenantQuery.setMinimumNumberShouldMatch(1);
        return new BooleanQuery.Builder()
                .add(new TermQuery(new Term(AgentIndexFields.KIND, kind)), BooleanClause.Occur.MUST)
                .add(tenantQuery.build(), BooleanClause.Occur.MUST)
                .build();
    }

    /**
     * 批量向量化知识块（不可用时返回全 {@code null}，块仍按 BM25 可用）。
     *
     * @param chunks 知识块
     * @return 与入参等长的向量列表
     */
    private List<float[]> embedChunks(List<Chunk> chunks) {
        if (!embeddingService.available()) {
            return new ArrayList<>(Collections.nCopies(chunks.size(), (float[]) null));
        }
        List<String> texts = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            texts.add(embedText(chunk));
        }
        List<float[]> vectors = embeddingService.embedBatch(texts);
        if (vectors.size() != chunks.size()) {
            return new ArrayList<>(Collections.nCopies(chunks.size(), (float[]) null));
        }
        return vectors;
    }

    /**
     * 组装向量化输入（标题 + 正文，超长截断）。
     *
     * @param chunk 知识块
     * @return 待向量化文本
     */
    private String embedText(Chunk chunk) {
        String text = nullToEmpty(chunk.getTitle()) + "\n" + nullToEmpty(chunk.getContent());
        return text.length() <= MAX_EMBED_CHARS ? text : text.substring(0, MAX_EMBED_CHARS);
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
            refreshIndexedMeta();
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
        return search(question, topK, kinds, null);
    }

    /**
     * BM25 检索（带权限过滤）。
     *
     * @param question 用户问题
     * @param topK 返回条数
     * @param kinds 知识类型过滤（空表示不过滤）
     * @param permissionCodes 用户权限码（{@code null} 表示不启用权限过滤；空集合表示仅普通知识块）
     * @return 命中结果（按分数降序）
     */
    public List<SearchHit> search(String question, int topK, Set<String> kinds, Set<String> permissionCodes) {
        if (!StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        IndexSearcher searcher = acquireSearcher();
        try {
            Query query = buildQuery(question, kinds, permissionCodes);
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
     * 向量（KNN）检索。
     *
     * @param vector 查询向量（须与索引维度一致）
     * @param topK 返回条数
     * @param kinds 知识类型过滤（空表示不过滤）
     * @return 命中结果（按余弦相似度降序）；索引无向量或维度不符返回空列表
     */
    public List<SearchHit> searchVector(float[] vector, int topK, Set<String> kinds) {
        return searchVector(vector, topK, kinds, null);
    }

    /**
     * 向量（KNN）检索（带权限过滤）。
     *
     * @param vector 查询向量（须与索引维度一致）
     * @param topK 返回条数
     * @param kinds 知识类型过滤（空表示不过滤）
     * @param permissionCodes 用户权限码（{@code null} 表示不启用权限过滤）
     * @return 命中结果（按余弦相似度降序）；索引无向量或维度不符返回空列表
     */
    public List<SearchHit> searchVector(float[] vector, int topK, Set<String> kinds,
                                        Set<String> permissionCodes) {
        if (vector == null || topK <= 0 || indexedDimension <= 0 || vector.length != indexedDimension) {
            return List.of();
        }
        IndexSearcher searcher = acquireSearcher();
        try {
            Query filter = combineFilters(
                    combineFilters(kindFilter(kinds), permissionFilter(permissionCodes)),
                    combineFilters(tenantFilter(), ownerFilter()));
            Query query = filter == null
                    ? new KnnFloatVectorQuery(AgentIndexFields.VECTOR, vector, topK)
                    : new KnnFloatVectorQuery(AgentIndexFields.VECTOR, vector, topK, filter);
            TopDocs topDocs = searcher.search(query, topK);
            StoredFields storedFields = searcher.storedFields();
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                hits.add(toHit(storedFields.document(scoreDoc.doc), scoreDoc.score));
            }
            return hits;
        } catch (IOException e) {
            throw new UncheckedIOException("向量检索失败", e);
        } finally {
            releaseSearcher(searcher);
        }
    }

    /**
     * 当前索引的向量维度。
     *
     * @return 维度；无向量返回 0
     */
    public int indexedDimension() {
        return indexedDimension;
    }

    /**
     * 当前索引的 embedding 签名（如 {@code hash:256}；未写入向量为 {@code none}）。
     *
     * @return 签名
     */
    public String indexedSignature() {
        return indexedSignature;
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
     * @param permissionCodes 用户权限码（{@code null} 表示不过滤）
     * @return Lucene 查询
     */
    private Query buildQuery(String question, Set<String> kinds, Set<String> permissionCodes) {
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
            boolean identifier = isIdentifierToken(token);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.TITLE_CODE, token)),
                    identifier ? 6.0f : 2.5f), BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.CONTENT_CODE, token)), 1.2f),
                    BooleanClause.Occur.SHOULD);
            builder.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.PATH_TEXT, token)),
                    identifier ? 4.0f : 1.8f), BooleanClause.Occur.SHOULD);
        }
        if (kinds != null && !kinds.isEmpty()) {
            Query filter = kindFilter(kinds);
            if (filter != null) {
                builder.add(filter, BooleanClause.Occur.FILTER);
            }
        }
        Query visible = permissionFilter(permissionCodes);
        if (visible != null) {
            builder.add(visible, BooleanClause.Occur.FILTER);
        }
        builder.add(tenantFilter(), BooleanClause.Occur.FILTER);
        builder.add(ownerFilter(), BooleanClause.Occur.FILTER);
        BooleanQuery.Builder outer = new BooleanQuery.Builder();
        outer.add(builder.build(), BooleanClause.Occur.MUST);
        if (isCodeIntent(question)) {
            outer.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.KIND, "code")), 6.0f),
                    BooleanClause.Occur.SHOULD);
        }
        if (isDocIntent(question)) {
            outer.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.KIND, "doc")), 4.0f),
                    BooleanClause.Occur.SHOULD);
            outer.add(new BoostQuery(new TermQuery(new Term(AgentIndexFields.KIND,
                    KnowledgeConstants.KIND_DOC_PAGE)), 4.0f), BooleanClause.Occur.SHOULD);
        }
        return outer.build();
    }

    /**
     * 构建权限过滤：无权限码的知识块恒可见，受限知识块须命中用户权限码之一。
     *
     * @param permissionCodes 用户权限码（{@code null} 表示不启用过滤）
     * @return 过滤查询；未启用返回 {@code null}
     */
    private Query permissionFilter(Set<String> permissionCodes) {
        if (permissionCodes == null) {
            return null;
        }
        BooleanQuery publicOnly = new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(AgentIndexFields.PERMISSION_RESTRICTED, "1")),
                        BooleanClause.Occur.MUST_NOT)
                .build();
        BooleanQuery.Builder visible = new BooleanQuery.Builder();
        visible.add(publicOnly, BooleanClause.Occur.SHOULD);
        for (String code : permissionCodes) {
            if (StringUtils.hasText(code)) {
                visible.add(new TermQuery(new Term(AgentIndexFields.PERMISSION_CODE, code)),
                        BooleanClause.Occur.SHOULD);
            }
        }
        visible.setMinimumNumberShouldMatch(1);
        return visible.build();
    }

    /**
     * 构建归属人过滤：无归属人字段的知识块（代码/文档/档案/公共文件）恒可见，
     * 带归属人的文件知识块仅归属人可见。
     *
     * <p>当前用户未知（内部调度、无身份的内部调用等）时按空用户处理，只放行公共知识，
     * 防止个人文件被无身份链路读到。</p>
     *
     * @return 归属人过滤查询
     */
    private Query ownerFilter() {
        BooleanQuery.Builder visible = new BooleanQuery.Builder()
                .add(missingOwnerQuery(), BooleanClause.Occur.SHOULD);
        Long owner = KnowledgePermissionContext.currentOwner();
        if (owner != null) {
            visible.add(new TermQuery(new Term(AgentIndexFields.OWNER_ID, String.valueOf(owner))),
                    BooleanClause.Occur.SHOULD);
        }
        visible.setMinimumNumberShouldMatch(1);
        return visible.build();
    }

    /**
     * 匹配缺少归属人字段的知识块（公共知识：代码、文档、档案快照与公共文件）。
     *
     * @return 缺少归属人字段的查询
     */
    private Query missingOwnerQuery() {
        return new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(new FieldExistsQuery(AgentIndexFields.OWNER_ID), BooleanClause.Occur.MUST_NOT)
                .build();
    }

    /**
     * 构建租户过滤：仅命中「当前租户」与「平台级知识（租户 0）」，并兼容升级前无租户字段的历史文档。
     *
     * @return 租户过滤查询
     */
    private Query tenantFilter() {
        Long tenant = TenantContext.get();
        long tenantId = tenant == null ? TenantContext.DEFAULT_TENANT_ID : tenant;
        BooleanQuery.Builder filter = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(AgentIndexFields.TENANT, String.valueOf(tenantId))),
                        BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term(AgentIndexFields.TENANT, String.valueOf(PLATFORM_TENANT_ID))),
                        BooleanClause.Occur.SHOULD)
                .add(missingTenantQuery(), BooleanClause.Occur.SHOULD);
        filter.setMinimumNumberShouldMatch(1);
        return filter.build();
    }

    /**
     * 匹配缺少租户字段的历史文档（升级前索引）。
     *
     * @return 缺少租户字段的查询
     */
    private Query missingTenantQuery() {
        return new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(new FieldExistsQuery(AgentIndexFields.TENANT), BooleanClause.Occur.MUST_NOT)
                .build();
    }

    /**
     * 合并两个过滤查询（任一为空返回另一个）。
     *
     * @param kindFilter 类型过滤
     * @param visible 权限过滤
     * @return 合并查询；均为空返回 {@code null}
     */
    private Query combineFilters(Query kindFilter, Query visible) {
        if (kindFilter == null) {
            return visible;
        }
        if (visible == null) {
            return kindFilter;
        }
        return new BooleanQuery.Builder()
                .add(kindFilter, BooleanClause.Occur.MUST)
                .add(visible, BooleanClause.Occur.MUST)
                .build();
    }

    /**
     * 构建知识类型过滤查询（BM25 与向量通道共用）。
     *
     * @param kinds 类型集合
     * @return 过滤查询；空集合返回 {@code null}
     */
    private Query kindFilter(Set<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder filter = new BooleanQuery.Builder();
        for (String kind : kinds) {
            filter.add(new TermQuery(new Term(AgentIndexFields.KIND, kind)), BooleanClause.Occur.SHOULD);
        }
        return filter.build();
    }

    /**
     * 判断是否为类名/文件名式标识符（小写 ASCII、长度 ≥11），用于路径与标题加权。
     *
     * <p>长度门槛刻意高于通用词（如 {@code permission}、{@code workflow}），
     * 只让 {@code chatservice}、{@code agentindexservice} 这类全名生效。</p>
     *
     * @param token 检索词
     * @return 标识符返回 {@code true}
     */
    private boolean isIdentifierToken(String token) {
        if (token == null || token.length() < 11) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 'a' || c > 'z') {
                if (c < '0' || c > '9') {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 判断是否为代码意图（命中类/文件/实现等词时加权 code 切块）。
     *
     * @param question 用户问题
     * @return 代码意图返回 {@code true}
     */
    private boolean isCodeIntent(String question) {
        String lower = question.toLowerCase();
        for (String word : List.of("类", "文件", "源码", "代码", "实现", "脚本", "扫描", "拦截器", "过滤器", "采集器")) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为文档意图（命中文档/手册/规范等词时加权 doc 切块）。
     *
     * @param question 用户问题
     * @return 文档意图返回 {@code true}
     */
    private boolean isDocIntent(String question) {
        String lower = question.toLowerCase();
        for (String word : List.of("文档", "手册", "规范", "约定", "指南")) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
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
     * @param vector 向量（可为 {@code null}）
     * @param signature embedding 签名
     * @param fileId 文件服务文件ID（可为 {@code null}）
     * @param permissionCode 所需权限码（可为 {@code null}）
     * @param tenantId 租户ID
     * @param ownerId 归属人ID（可为 {@code null}）
     * @return Lucene 文档
     */
    private Document toDocument(Chunk chunk, String hash, int seq, float[] vector, String signature,
                                Long fileId, DocIndexMeta docMeta, String permissionCode, long tenantId,
                                Long ownerId) {
        String content = nullToEmpty(chunk.getContent());
        String title = nullToEmpty(chunk.getTitle());
        Document document = new Document();
        document.add(new StringField(AgentIndexFields.ID,
                chunk.getKind() + ":" + chunk.getPath() + ":" + hash + ":" + seq, Field.Store.YES));
        String tenantValue = String.valueOf(tenantId);
        document.add(new StringField(AgentIndexFields.TENANT, tenantValue, Field.Store.YES));
        document.add(new SortedDocValuesField(AgentIndexFields.TENANT, new BytesRef(tenantValue)));
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
        if (vector != null && vector.length > 0) {
            document.add(new KnnFloatVectorField(AgentIndexFields.VECTOR, vector,
                    VectorSimilarityFunction.COSINE));
        }
        document.add(new StoredField(AgentIndexFields.VECTOR_DIM, vector == null ? 0 : vector.length));
        document.add(new StoredField(AgentIndexFields.EMBED_SIG, nullToEmpty(signature)));
        if (fileId != null) {
            document.add(new StringField(AgentIndexFields.FILE_ID, String.valueOf(fileId), Field.Store.YES));
        }
        if (ownerId != null) {
            String ownerValue = String.valueOf(ownerId);
            document.add(new StringField(AgentIndexFields.OWNER_ID, ownerValue, Field.Store.YES));
            document.add(new SortedDocValuesField(AgentIndexFields.OWNER_ID, new BytesRef(ownerValue)));
        }
        if (docMeta != null) {
            addDocMeta(document, docMeta);
        }
        if (StringUtils.hasText(permissionCode)) {
            document.add(new StringField(AgentIndexFields.PERMISSION_RESTRICTED, "1", Field.Store.NO));
            document.add(new StringField(AgentIndexFields.PERMISSION_CODE, permissionCode, Field.Store.YES));
            document.add(new SortedDocValuesField(AgentIndexFields.PERMISSION_CODE,
                    new BytesRef(permissionCode)));
        }
        return document;
    }

    /**
     * 写入文档页面元数据存储字段。
     *
     * @param document Lucene 文档
     * @param meta     页面元数据
     */
    private void addDocMeta(Document document, DocIndexMeta meta) {
        if (meta.pageId() != null) {
            document.add(new StringField(AgentIndexFields.DOC_PAGE_ID, String.valueOf(meta.pageId()),
                    Field.Store.YES));
        }
        if (meta.spaceId() != null) {
            document.add(new StringField(AgentIndexFields.SPACE_ID, String.valueOf(meta.spaceId()),
                    Field.Store.YES));
        }
        if (StringUtils.hasText(meta.url())) {
            document.add(new StoredField(AgentIndexFields.URL, meta.url()));
        }
    }

    /**
     * 读取索引当前的向量维度与 embedding 签名（取任一存活文档的存储字段）。
     */
    private void refreshIndexedMeta() {
        IndexSearcher searcher = acquireSearcher();
        try {
            indexedDimension = 0;
            indexedSignature = "none";
            for (LeafReaderContext context : searcher.getIndexReader().leaves()) {
                if (context.reader().numDocs() <= 0) {
                    continue;
                }
                Document document = context.reader().storedFields().document(0);
                indexedDimension = intValue(document.get(AgentIndexFields.VECTOR_DIM));
                indexedSignature = nullToEmpty(document.get(AgentIndexFields.EMBED_SIG));
                return;
            }
        } catch (IOException e) {
            log.warn("读取索引向量元数据失败", e);
        } finally {
            releaseSearcher(searcher);
        }
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
                .fileId(longValue(document.get(AgentIndexFields.FILE_ID)))
                .url(document.get(AgentIndexFields.URL))
                .score(score)
                .build();
    }

    /**
     * 读取 StoredField 中的长整型。
     *
     * @param value 字符串值
     * @return 数值；缺失或非法返回 {@code null}
     */
    private Long longValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
                .dimension(indexedDimension)
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
