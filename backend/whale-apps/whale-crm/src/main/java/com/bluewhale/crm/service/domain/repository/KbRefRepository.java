package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.KbRef;
import com.bluewhale.crm.service.domain.model.KbRefQuery;

import java.util.List;

/**
 * 知识引用仓储。
 *
 * @author BlueWhale
 */
public interface KbRefRepository {

    /**
     * 分页查询知识引用。
     *
     * @param query 查询条件
     * @return 当前页数据
     */
    List<KbRef> page(KbRefQuery query);

    /**
     * 统计知识引用总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(KbRefQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 知识引用
     */
    KbRef findById(Long id);

    /**
     * 新增知识引用。
     *
     * @param ref 知识引用
     */
    void insert(KbRef ref);

    /**
     * 更新知识引用。
     *
     * @param ref 知识引用
     */
    void update(KbRef ref);

    /**
     * 逻辑删除。
     *
     * @param id 主键
     */
    void deleteById(Long id);

    /**
     * 引用命中次数 +1。
     *
     * @param id 主键
     */
    void incrementHit(Long id);
}
