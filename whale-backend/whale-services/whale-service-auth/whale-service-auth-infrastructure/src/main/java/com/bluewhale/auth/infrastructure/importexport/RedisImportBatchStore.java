package com.bluewhale.auth.infrastructure.importexport;

import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.application.port.ImportBatchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * {@link ImportBatchStore} 的 Redis 实现（权限中心扩展 M4）。
 *
 * <p>预检通过行以 JSON 暂存（含归属租户，提交时校验防跨租户提交）；
 * TTL 随批次写入，过期即「预检不存在」，提示重新上传。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisImportBatchStore implements ImportBatchStore {

    /** key 前缀 */
    static final String KEY_PREFIX = "auth:import:batch:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String batchId, Long tenantId, List<UserImportRow> rows, Duration ttl) {
        try {
            StoredBatch batch = new StoredBatch(tenantId, rows);
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + batchId,
                    objectMapper.writeValueAsString(batch), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("导入批次暂存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StoredBatch load(String batchId, Long tenantId) {
        if (batchId == null || batchId.isBlank()) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + batchId);
        if (json == null) {
            return null;
        }
        try {
            StoredBatch batch = objectMapper.readValue(json, StoredBatch.class);
            // 归属租户不匹配视为不存在，防跨租户提交
            if (!tenantId.equals(batch.tenantId())) {
                return null;
            }
            return batch;
        } catch (Exception e) {
            log.warn("导入批次载入失败，视为过期: batchId={}, {}", batchId, e.getMessage());
            return null;
        }
    }

    @Override
    public void delete(String batchId) {
        stringRedisTemplate.delete(KEY_PREFIX + batchId);
    }
}
