package com.bluewhale.lms.cert.domain.repository;

import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.Certificate;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;

import java.util.List;
import java.util.Optional;

/**
 * 证书仓储。
 *
 * @author BlueWhale
 */
public interface CertificateRepository {

    /**
     * 分页查询证书。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 证书列表
     */
    List<Certificate> page(CertificateQuery query, long offset, int size);

    /**
     * 统计证书总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CertificateQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 证书 ID
     * @return 证书
     */
    Optional<Certificate> findById(Long id);

    /**
     * 按编号查询。
     *
     * @param certNo 证书编号
     * @return 证书
     */
    Optional<Certificate> findByNo(String certNo);

    /**
     * 查询某来源已发出的首次证书（重复发证校验用）。
     *
     * @param templateId 模板 ID
     * @param userId 持证人用户 ID
     * @param sourceType 来源类型
     * @param sourceId 来源业务 ID
     * @return 证书
     */
    Optional<Certificate> findFirstIssue(Long templateId, Long userId, CertSource sourceType, Long sourceId);

    /**
     * 查询某年份已用的最大流水号（无记录返回 0）。
     *
     * <p>不能用 {@code COUNT(*)} 取号：证书一旦逻辑删除，条数会回退，
     * 下一个证就会拿到已用过的流水号并撞上唯一键。</p>
     *
     * @param year 发证年份
     * @return 最大流水号
     */
    int findMaxSeqNo(int year);

    /**
     * 保存证书。
     *
     * @param certificate 证书
     * @return 主键
     */
    Long save(Certificate certificate);

    /**
     * 分页查询某持证人的证书（发证时间倒序）。
     *
     * @param userId 持证人用户 ID
     * @param offset 偏移量
     * @param size 条数
     * @return 证书列表
     */
    List<Certificate> pageByUser(Long userId, long offset, int size);

    /**
     * 统计某持证人的证书数。
     *
     * @param userId 持证人用户 ID
     * @return 总数
     */
    long countByUser(Long userId);

    /**
     * 统计某模板已发证书数（删除模板前的引用校验）。
     *
     * @param templateId 模板 ID
     * @return 证书数
     */
    long countByTemplate(Long templateId);
}
