package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.MallMember;

import java.util.List;

/**
 * 商城会员仓储。
 *
 * @author BlueWhale
 */
public interface MemberRepository {

    /**
     * 按条件分页查询。
     *
     * @param keyword 会员号/用户 ID（模糊，可空）
     * @param status  状态（可空）
     * @param offset  偏移量
     * @param size    页大小
     * @return 会员列表
     */
    List<MallMember> page(String keyword, String status, long offset, int size);

    /**
     * 按条件统计总数。
     *
     * @param keyword 会员号/用户 ID（模糊，可空）
     * @param status  状态（可空）
     * @return 总数
     */
    long count(String keyword, String status);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 会员；不存在或已删除返回空
     */
    MallMember findById(Long id);

    /**
     * 按用户 ID 查询（会员价判定用）。
     *
     * @param userId 平台用户 ID
     * @return 会员；未登记返回空
     */
    MallMember findByUserId(Long userId);

    /**
     * 按用户 ID 登记（存在则更新，用户是会员的自然键）。
     *
     * @param member 会员
     * @return 主键
     */
    Long save(MallMember member);

    /**
     * 逻辑删除。
     *
     * @param id 主键
     */
    void delete(Long id);
}
