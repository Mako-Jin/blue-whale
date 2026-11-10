package com.bluewhale.lms.integration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.integration.infrastructure.persistence.entity.MemberSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工只读快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MemberSnapshotMapper extends BaseMapper<MemberSnapshotEntity> {
}
