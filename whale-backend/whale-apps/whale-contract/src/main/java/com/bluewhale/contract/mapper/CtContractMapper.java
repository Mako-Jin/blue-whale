package com.bluewhale.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.contract.entity.CtContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 合同主表 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface CtContractMapper extends BaseMapper<CtContract> {

    /**
     * 查询指定前缀下已使用的最大编号（含逻辑删除行，避免编号复用触发唯一索引冲突）。
     *
     * @param prefix 编号前缀，如 {@code HT-CG-2026-}
     * @return 最大编号，无记录返回 {@code null}
     */
    @Select("SELECT MAX(code) FROM ct_contract WHERE code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCodeByPrefix(@Param("prefix") String prefix);

    /**
     * 统计编号占用数（含逻辑删除行，唯一索引不区分删除标记）。
     *
     * @param code 合同编号
     * @return 占用行数
     */
    @Select("SELECT COUNT(1) FROM ct_contract WHERE code = #{code}")
    long countByCodeIncludingDeleted(@Param("code") String code);
}
