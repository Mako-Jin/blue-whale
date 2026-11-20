package com.bluewhale.audit.retention.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志归档水位实体，对应 {@code audit_archive_watermark} 表（AUTH-09）。
 *
 * <p>归档任务<b>不删除</b> {@code audit_log}（append-only），若每轮都按「早于保留期」全量查询，
 * 同一批过期记录会被反复导出、重复上传。本表单行（{@code id} 恒为 1）记录上一轮成功归档批次的
 * 最后一条 {@code create_time + id}，下一轮据此增量续查，保证每条记录只导出一轮。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("audit_archive_watermark")
public class AuditArchiveWatermarkPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定单行主键，恒为 1 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 已归档批次的最后一条 {@code audit_log.create_time} */
    private LocalDateTime lastCreateTime;

    /** 已归档批次的最后一条 {@code audit_log.id}（同一 {@code create_time} 内去重） */
    private Long lastId;

    /** 水位更新时间 */
    private LocalDateTime updateTime;

    /**
     * 本批归档 CSV 内容的 SHA-256（十六进制小写）
     *
     * <p>对单个归档文件可自证「下载到的文件就是当初上传的文件」：重算 SHA-256 与本列比对即可。</p>
     */
    private String lastBatchSha256;

    /**
     * 链式哈希 {@code sha256(上一批 chain_sha256 || 本批 last_batch_sha256)}（十六进制小写）
     *
     * <p>首批以上一批为空串参与计算。写者无须密钥即可保证「批与批之间连续、无插入无删除」；
     * 若再配置 {@code whale.audit.export.sign-key}，清单里还会有 HMAC 签名，连有库写权限的人
     * 也无法无痕伪造整条链（详见 {@code docs/reviews/2026-11-20-three-admin-compliance.md}）。</p>
     */
    private String chainSha256;

    /** 本批归档 CSV 的对象存储键（定位原件用） */
    private String lastObjectKey;

    /** 本批 {@code .sha256} 校验清单的对象存储键（清单内含哈希、区间、行数、可选 HMAC） */
    private String lastManifestKey;

    /** 本批归档行数 */
    private Integer lastBatchRows;

    /** 累计归档行数（跨批对账用） */
    private Long archivedTotal;
}
