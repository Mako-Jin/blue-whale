package com.bluewhale.lms.cert.domain.repository;

import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateQuery;

import java.util.List;
import java.util.Optional;

/**
 * 证书模板仓储。
 *
 * @author BlueWhale
 */
public interface CertTemplateRepository {

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 模板列表
     */
    List<CertTemplate> page(CertTemplateQuery query, long offset, int size);

    /**
     * 统计模板总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CertTemplateQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 模板 ID
     * @return 模板
     */
    Optional<CertTemplate> findById(Long id);

    /**
     * 按编码查询（编码冲突校验用）。
     *
     * @param templateCode 归一后的模板编码
     * @return 模板
     */
    Optional<CertTemplate> findByCode(String templateCode);

    /**
     * 保存模板。
     *
     * @param template 模板
     * @return 主键
     */
    Long save(CertTemplate template);

    /**
     * 逻辑删除模板。
     *
     * @param id 模板 ID
     */
    void deleteById(Long id);
}
