package com.bluewhale.oa.attendance.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceGroupMemberEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 考勤组成员 Mapper。
 *
 * @author BlueWhale
 */
public interface AttendanceGroupMemberMapper extends BaseMapper<AttendanceGroupMemberEntity> {

    /**
     * 物理删除指定考勤组的成员关系，供成员整组替换使用。
     *
     * @param groupId 考勤组ID
     * @return 删除行数
     */
    @Delete("DELETE FROM oa_att_group_member WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);
}
