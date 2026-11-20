package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.SensitiveWordSaveRequest;
import com.bluewhale.forum.dto.SensitiveWordVO;
import com.bluewhale.forum.entity.ForumSensitiveHit;
import com.bluewhale.forum.entity.ForumSensitiveWord;
import com.bluewhale.forum.mapper.ForumSensitiveHitMapper;
import com.bluewhale.forum.mapper.ForumSensitiveWordMapper;
import com.bluewhale.forum.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感词扫描实现：租户级 Trie 缓存 + 三档动作取最严。
 *
 * <p><b>为什么按租户懒加载而不是启动全量加载</b>：本服务多租户共用一套表，
 * 启动时没有 {@code TenantContext}，MP 租户插件会把查询变成
 * {@code tenant_id = null} 的空结果——预热看似成功、实际词库为空，
 * 于是「敏感词过滤全部放行」这种静默失效。改为首次使用时按当前租户加载，
 * 管理端改词后 {@link #reload()} 重建。</p>
 *
 * <p><b>为什么用 Trie 而不是逐个 {@code indexOf}</b>：几千词 × 10 万字正文的
 * {@code indexOf} 是 O(n·m) 次扫描，发帖接口会被拖到秒级；Trie 单趟 O(n·maxLen)。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    /** 动作严重度：数值大者优先 */
    private static final Map<String, Integer> RANK = Map.of(
            ScanResult.NONE, 0,
            ScanResult.REPLACE, 1,
            ScanResult.PENDING, 2,
            ScanResult.BLOCK, 3);

    private final ForumSensitiveWordMapper wordMapper;

    private final ForumSensitiveHitMapper hitMapper;

    private final ForumProperties properties;

    /** 租户 → 词库（懒加载；reload 时移除该项） */
    private final Map<Long, WordBank> cache = new ConcurrentHashMap<>();

    @Override
    public ScanResult scan(String text) {
        if (text == null || text.isEmpty()) {
            return new ScanResult(ScanResult.NONE, null, -1, text);
        }
        WordBank bank = bank();
        if (bank.isEmpty()) {
            return new ScanResult(ScanResult.NONE, null, -1, text);
        }
        List<Hit> hits = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            Node node = bank.root();
            for (int j = i; j < length; j++) {
                node = node.children.get(Character.toLowerCase(text.charAt(j)));
                if (node == null) {
                    break;
                }
                if (node.word != null) {
                    hits.add(new Hit(i, j + 1, node.word, node.action));
                }
            }
        }
        if (hits.isEmpty()) {
            return new ScanResult(ScanResult.NONE, null, -1, text);
        }
        Hit strictest = hits.stream()
                .max(Comparator.comparingInt((Hit h) -> RANK.getOrDefault(h.action(), 0))
                        .thenComparingInt(Hit::end))
                .orElseThrow();
        String finalText = ScanResult.BLOCK.equals(strictest.action())
                ? text
                : maskReplaced(text, hits);
        return new ScanResult(strictest.action(), strictest.word(), strictest.start(), finalText);
    }

    @Override
    public int reload() {
        Long tenantId = TenantContext.requireId();
        cache.remove(tenantId);
        return bank().size();
    }

    @Override
    public int cachedWordCount() {
        Long tenantId = TenantContext.get();
        WordBank bank = tenantId == null ? null : cache.get(tenantId);
        return bank == null ? 0 : bank.size();
    }

    @Override
    public PageResult<SensitiveWordVO> page(String keyword, Long current, Long size) {
        TenantContext.requireId();
        long pageNo = current == null || current < 1 ? 1 : Math.min(current, properties.getMaxPageNumber());
        long pageSize = size == null || size < 1
                ? properties.getPageSizeDefault()
                : Math.min(size, properties.getPageSizeMax());
        // 字符串列名：lambda 依赖 TableInfo 缓存，纯单测会抛 "can not find lambda cache"
        QueryWrapper<ForumSensitiveWord> query = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            query.like("word", keyword.trim().toLowerCase());
        }
        query.orderByDesc("create_time");
        IPage<ForumSensitiveWord> page = wordMapper.selectPage(new Page<>(pageNo, pageSize), query);
        List<SensitiveWordVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumSensitiveWord row : page.getRecords()) {
            SensitiveWordVO vo = new SensitiveWordVO();
            vo.setId(row.getId());
            vo.setWord(row.getWord());
            vo.setAction(row.getAction());
            vo.setCategory(row.getCategory());
            vo.setStatus(row.getStatus());
            vo.setCreateTime(row.getCreateTime());
            records.add(vo);
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SensitiveWordSaveRequest request, Long operatorId) {
        Long tenantId = TenantContext.requireId();
        String word = request.getWord().trim().toLowerCase();
        if (word.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.sensitive-word.empty", "敏感词不能为空");
        }
        ForumSensitiveWord existing = wordMapper.selectOne(new QueryWrapper<ForumSensitiveWord>()
                .eq("word", word)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setAction(request.getAction());
            existing.setCategory(request.getCategory());
            existing.setStatus(1);
            wordMapper.updateById(existing);
            cache.remove(tenantId);
            return existing.getId();
        }
        ForumSensitiveWord row = new ForumSensitiveWord();
        row.setTenantId(tenantId);
        row.setWord(word);
        row.setAction(request.getAction());
        row.setCategory(request.getCategory());
        row.setStatus(1);
        row.setCreatorId(operatorId);
        wordMapper.insert(row);
        cache.remove(tenantId);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Long tenantId = TenantContext.requireId();
        wordMapper.hardDelete(id);
        cache.remove(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer status) {
        Long tenantId = TenantContext.requireId();
        ForumSensitiveWord row = wordMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.sensitive-word.not-found", "词条不存在");
        }
        row.setStatus(status == null || status == 0 ? 0 : 1);
        wordMapper.updateById(row);
        cache.remove(tenantId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordHit(String targetType, Long targetId, Long userId, ScanResult result) {
        if (result == null || !result.hit()) {
            return;
        }
        try {
            ForumSensitiveHit hit = new ForumSensitiveHit();
            hit.setTenantId(TenantContext.requireId());
            hit.setTargetType(targetType);
            hit.setTargetId(targetId);
            hit.setUserId(userId);
            hit.setWord(result.hitWord());
            hit.setPosition(result.position());
            hit.setAction(result.action());
            hit.setCreateTime(LocalDateTime.now());
            hitMapper.insert(hit);
        } catch (RuntimeException error) {
            log.warn("敏感词命中留痕失败（不影响主流程）：word={}, reason={}",
                    result.hitWord(), error.getMessage());
        }
    }

    /**
     * 把所有 REPLACE 级命中的区间替换为等长星号（从后往前替换，避免索引错位）。
     *
     * @param text 原文
     * @param hits 全部命中
     * @return 打码后的文本
     */
    private String maskReplaced(String text, List<Hit> hits) {
        List<Hit> replaces = hits.stream()
                .filter(h -> ScanResult.REPLACE.equals(h.action()))
                .sorted(Comparator.comparingInt(Hit::start).reversed())
                .toList();
        if (replaces.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (Hit h : replaces) {
            for (int i = h.start(); i < h.end() && i < sb.length(); i++) {
                sb.setCharAt(i, '*');
            }
        }
        return sb.toString();
    }

    /**
     * 取当前租户词库（未加载则加载一次）。
     *
     * @return 词库
     */
    private WordBank bank() {
        Long tenantId = TenantContext.requireId();
        return cache.computeIfAbsent(tenantId, this::load);
    }

    /**
     * 从库里加载某租户全部启用词条并构建 Trie。
     *
     * @param tenantId 租户ID
     * @return 词库
     */
    private WordBank load(Long tenantId) {
        List<ForumSensitiveWord> words = wordMapper.selectList(
                new QueryWrapper<ForumSensitiveWord>().eq("status", 1));
        WordBank bank = new WordBank();
        for (ForumSensitiveWord row : words) {
            if (row.getWord() == null || row.getWord().isBlank()) {
                continue;
            }
            bank.put(row.getWord().trim(), row.getAction() == null ? ScanResult.BLOCK : row.getAction());
        }
        log.info("敏感词库加载完成：tenantId={}, words={}", tenantId, bank.size());
        return bank;
    }

    /**
     * 一次命中的区间。
     *
     * @param start 起始偏移（含）
     * @param end 结束偏移（不含）
     * @param word 命中词
     * @param action 词条动作
     */
    private record Hit(int start, int end, String word, String action) {
    }

    /**
     * 词库（Trie）。
     */
    private static final class WordBank {

        private final Node root = new Node();

        private int size;

        /**
         * 返回 Trie 根节点，供匹配时从根开始遍历。
         *
         * @return Trie 根节点
         */
        Node root() {
            return root;
        }

        /**
         * 判断词库是否为空（未装载任何敏感词）。
         *
         * @return 空词库返回 true
         */
        boolean isEmpty() {
            return size == 0;
        }

        /**
         * 返回已装载的敏感词数量。
         *
         * @return 敏感词条数
         */
        int size() {
            return size;
        }

        /**
         * 写入一个敏感词：按小写字符逐层挂到 Trie 上，末节点记录原词与命中动作。
         *
         * @param word 敏感词原文
         * @param action 命中后的处置动作
         */
        void put(String word, String action) {
            Node node = root;
            String lower = word.toLowerCase();
            for (int i = 0; i < lower.length(); i++) {
                node = node.children.computeIfAbsent(lower.charAt(i), k -> new Node());
            }
            node.word = word;
            node.action = action;
            size++;
        }
    }

    /**
     * Trie 节点。
     */
    private static final class Node {

        private final Map<Character, Node> children = new HashMap<>(4);

        /** 终结词（非空表示该节点是一个完整词） */
        private String word;

        /** 动作 */
        private String action;
    }
}
