package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.importexport.UserImportRow;

import java.time.Duration;
import java.util.List;

/**
 * 导入批次暂存端口（权限中心扩展 M4）：预检通过行在提交前暂存（Redis，30 分钟有效）。
 *
 * <p>导入采用「预检 → 暂存 → 提交」两段式：提交时按 batchId 取回暂存行，
 * 不重复解析与校验上传文件。</p>
 *
 * @author BlueWhale
 */
public interface ImportBatchStore {

    /**
     * 暂存预检通过的导入行
     *
     * @param batchId  批次 ID
     * @param tenantId 租户 ID（提交时校验归属，防跨租户提交）
     * @param rows     预检通过的导入行
     * @param ttl      有效期
     */
    void save(String batchId, Long tenantId, List<UserImportRow> rows, Duration ttl);

    /**
     * 读取暂存批次
     *
     * @param batchId  批次 ID
     * @param tenantId 租户 ID（不匹配视为不存在）
     * @return 暂存批次；不存在/已过期/租户不匹配时返回 {@code null}
     */
    StoredBatch load(String batchId, Long tenantId);

    /**
     * 删除暂存批次（提交后清理，防重放）
     *
     * @param batchId 批次 ID
     */
    void delete(String batchId);

    /**
     * 暂存批次：归属租户 + 通过行集合
     *
     * @param tenantId 租户 ID
     * @param rows     导入行列表
     * @author BlueWhale
     */
    record StoredBatch(Long tenantId, List<UserImportRow> rows) {
    }
}
