package com.bluewhale.map.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.map.config.MapProperties;
import com.bluewhale.map.entity.MapGeocodeCache;
import com.bluewhale.map.mapper.MapGeocodeCacheMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 解析结果缓存。
 *
 * <p><b>缓存的是「对外结果」而不是「供应商原始响应」</b>：原始响应里带着各家自己的坐标系与
 * 字段命名，缓存它等于把供应商差异固化进数据库，换供应商时必须整表清空。</p>
 *
 * <p><b>键里必须有坐标系与供应商</b>：同一串数字配上不同坐标系是两个地点，
 * 同一地址在不同供应商可能落在相邻两栋楼。少放一段就会出现「切配置后位置自己变了」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapCacheSupport {

    /** 查询类型：地址解析 */
    public static final String TYPE_GEOCODE = "GEOCODE";

    /** 查询类型：逆地理 */
    public static final String TYPE_REGEO = "REGEO";

    /** 查询类型：地点检索 */
    public static final String TYPE_POI = "POI";

    /** 缓存表 Mapper */
    private final MapGeocodeCacheMapper cacheMapper;

    /** 服务配置 */
    private final MapProperties properties;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 规整查询文本：去首尾空白、压缩连续空白、转小写
     *
     * @param text 原始文本
     * @return 规整后文本
     */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 计算缓存键（SHA-256 十六进制）
     *
     * @param text 规整后的查询文本（可含多段）
     * @return 64 位十六进制串
     */
    public String keyOf(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256", ex);
        }
    }

    /**
     * 读取缓存
     *
     * @param provider 供应商名
     * @param type     查询类型
     * @param cacheKey 缓存键
     * @param clazz    结果类型
     * @param <T>      结果类型
     * @return 缓存结果；未命中或已过期返回 {@code null}
     */
    public <T> T get(String provider, String type, String cacheKey, Class<T> clazz) {
        if (!properties.getCache().isEnabled()) {
            return null;
        }
        MapGeocodeCache row = findRow(provider, type, cacheKey);
        if (row == null || row.getExpireTime() == null || row.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        try {
            T value = objectMapper.readValue(row.getResultJson(), clazz);
            cacheMapper.increaseHit(row.getId());
            return value;
        } catch (Exception ex) {
            // 缓存内容坏了就当没命中：化掉它比让整个接口 500 更划算
            log.warn("缓存内容无法解析，按未命中处理：provider={}，type={}，key={}", provider, type, cacheKey);
            return null;
        }
    }

    /**
     * 写入缓存（存在即覆盖）
     *
     * @param provider  供应商名
     * @param type      查询类型
     * @param cacheKey  缓存键
     * @param queryText 原始查询文本（排障用）
     * @param value     结果对象
     */
    public void put(String provider, String type, String cacheKey, String queryText, Object value) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        if (queryText != null && queryText.length() > properties.getCache().getMaxTextLength()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            MapGeocodeCache row = findRow(provider, type, cacheKey);
            if (row == null) {
                row = new MapGeocodeCache();
                row.setProvider(provider);
                row.setQueryType(type);
                row.setCacheKey(cacheKey);
                row.setQueryText(truncate(queryText));
                row.setResultJson(json);
                row.setHitCount(0L);
                row.setExpireTime(expireAt());
                insertQuietly(row);
                return;
            }
            row.setResultJson(json);
            row.setExpireTime(expireAt());
            cacheMapper.updateById(row);
        } catch (Exception ex) {
            // 缓存写失败不影响主流程：这次多花一次配额，下次还会再试
            log.warn("写缓存失败：provider={}，type={}，原因={}", provider, type, ex.getMessage());
        }
    }

    /**
     * 查询缓存行
     *
     * @param provider 供应商名
     * @param type     查询类型
     * @param cacheKey 缓存键
     * @return 缓存行；不存在返回 {@code null}
     */
    private MapGeocodeCache findRow(String provider, String type, String cacheKey) {
        LambdaQueryWrapper<MapGeocodeCache> wrapper = new LambdaQueryWrapper<MapGeocodeCache>()
                .eq(MapGeocodeCache::getProvider, provider)
                .eq(MapGeocodeCache::getQueryType, type)
                .eq(MapGeocodeCache::getCacheKey, cacheKey)
                .last("LIMIT 1");
        return cacheMapper.selectOne(wrapper);
    }

    /**
     * 插入缓存行，主键冲突视为已存在（并发下同一地址被同时解析）
     *
     * @param row 缓存行
     */
    private void insertQuietly(MapGeocodeCache row) {
        try {
            cacheMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            log.debug("缓存行已存在，跳过写入：provider={}，key={}", row.getProvider(), row.getCacheKey());
        }
    }

    /**
     * 计算过期时间
     *
     * @return 过期时间
     */
    private LocalDateTime expireAt() {
        return LocalDateTime.now().plusHours(properties.getCache().getTtlHours());
    }

    /**
     * 截断查询文本到列宽
     *
     * @param text 原始文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        int max = properties.getCache().getMaxTextLength();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
