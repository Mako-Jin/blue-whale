package com.bluewhale.party.fee.domain.repository;

import com.bluewhale.party.fee.domain.model.FeeRule;

import java.util.List;
import java.util.Optional;

/**
 * 党费标准仓储端口。
 *
 * @author BlueWhale
 */
public interface FeeRuleRepository {

    /**
     * 查询全部档位（按基数升序）。
     *
     * @return 档位列表
     */
    List<FeeRule> listAll();

    /**
     * 查询启用中的档位（按基数升序）。
     *
     * @return 档位列表
     */
    List<FeeRule> listActive();

    /**
     * 按主键查询。
     *
     * @param id 档位 ID
     * @return 档位
     */
    Optional<FeeRule> findById(Long id);

    /**
     * 保存档位。
     *
     * @param rule 档位
     * @return 主键
     */
    Long save(FeeRule rule);

    /**
     * 统计档位数量。
     *
     * @return 数量
     */
    long count();
}
