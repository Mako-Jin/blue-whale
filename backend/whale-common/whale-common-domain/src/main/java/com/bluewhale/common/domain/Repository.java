package com.bluewhale.common.domain;

import com.bluewhale.common.domain.model.Aggregate;

import java.util.Optional;

/**
 * 聚合仓储端口：契约定义于领域层，由基础设施层以持久化技术适配实现。
 *
 * @param <T> 聚合根类型
 * @param <ID> 聚合根主键类型
 * @author BlueWhale
 */
public interface Repository<T extends Aggregate<ID>, ID> {

    /**
     * 按主键查询聚合
     *
     * @param id 聚合根主键
     * @return 聚合根；不存在时返回空
     */
    Optional<T> findById(ID id);

    /**
     * 保存聚合（主键为空时新增，否则按主键更新）
     *
     * @param aggregate 聚合根
     * @return 保存后的聚合根（新增时主键已回填）
     */
    T save(T aggregate);

    /**
     * 删除聚合
     *
     * @param aggregate 待删除的聚合根
     */
    void delete(T aggregate);
}
