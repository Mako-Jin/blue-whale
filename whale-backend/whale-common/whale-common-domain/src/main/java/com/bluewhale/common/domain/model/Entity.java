package com.bluewhale.common.domain.model;

/**
 * 领域实体接口：以主键标识身份，同一性由主键判定而非属性值。
 *
 * <p>身份比较（{@code equals}/{@code hashCode}）由实现方按主键语义决定：新建且未落库的实体
 * 主键可能为 {@code null}，此时应退化为对象同一性比较。</p>
 *
 * @param <ID> 主键类型
 * @author BlueWhale
 */
public interface Entity<ID> {

    /**
     * 获取实体主键
     *
     * @return 主键；新建且尚未持久化时可为 {@code null}
     */
    ID getId();
}
