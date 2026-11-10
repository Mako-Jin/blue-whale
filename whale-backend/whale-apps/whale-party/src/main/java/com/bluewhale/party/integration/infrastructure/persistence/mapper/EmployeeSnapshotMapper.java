package com.bluewhale.party.integration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.integration.infrastructure.persistence.entity.EmployeeSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface EmployeeSnapshotMapper extends BaseMapper<EmployeeSnapshotEntity> {
}
