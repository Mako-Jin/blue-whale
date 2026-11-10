package com.bluewhale.oa.attendance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.oa.attendance.domain.model.AttendanceDevice;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroupMember;
import com.bluewhale.oa.attendance.domain.model.AttendanceLocation;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import com.bluewhale.oa.attendance.domain.repository.AttendanceConfigRepository;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceDeviceEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceGroupEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceGroupMemberEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceLocationEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceRuleEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.entity.AttendanceShiftEntity;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceDeviceMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceGroupMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceGroupMemberMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceLocationMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceRuleMapper;
import com.bluewhale.oa.attendance.infrastructure.persistence.mapper.AttendanceShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 考勤主数据仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AttendanceConfigRepositoryImpl implements AttendanceConfigRepository {

    private final AttendanceGroupMapper groupMapper;

    private final AttendanceGroupMemberMapper memberMapper;

    private final AttendanceShiftMapper shiftMapper;

    private final AttendanceRuleMapper ruleMapper;

    private final AttendanceLocationMapper locationMapper;

    private final AttendanceDeviceMapper deviceMapper;

    @Override
    public List<AttendanceGroup> listGroups() {
        return groupMapper.selectList(Wrappers.<AttendanceGroupEntity>lambdaQuery()
                        .orderByAsc(AttendanceGroupEntity::getCode))
                .stream().map(AttendanceConfigRepositoryImpl::toGroup).toList();
    }

    @Override
    public Optional<AttendanceGroup> findGroup(Long id) {
        return Optional.ofNullable(groupMapper.selectById(id))
                .map(AttendanceConfigRepositoryImpl::toGroup);
    }

    @Override
    public Optional<AttendanceGroup> findGroupByCode(String code) {
        return Optional.ofNullable(groupMapper.selectOne(Wrappers.<AttendanceGroupEntity>lambdaQuery()
                        .eq(AttendanceGroupEntity::getCode, code)))
                .map(AttendanceConfigRepositoryImpl::toGroup);
    }

    @Override
    public Optional<AttendanceGroup> findGroupByUserId(Long userId) {
        AttendanceGroupMemberEntity member = memberMapper.selectOne(Wrappers.<AttendanceGroupMemberEntity>lambdaQuery()
                .eq(AttendanceGroupMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            return Optional.empty();
        }
        return findGroup(member.getGroupId());
    }

    @Override
    public void saveGroup(AttendanceGroup group) {
        AttendanceGroupEntity entity = toGroupEntity(group);
        if (entity.getId() == null) {
            groupMapper.insert(entity);
        } else {
            groupMapper.updateById(entity);
        }
        group.setId(entity.getId());
    }

    @Override
    public void deleteGroup(Long id) {
        groupMapper.deleteById(id);
    }

    @Override
    public boolean existsGroupCode(String code, Long excludeId) {
        return groupMapper.selectCount(Wrappers.<AttendanceGroupEntity>lambdaQuery()
                .eq(AttendanceGroupEntity::getCode, code)
                .ne(excludeId != null, AttendanceGroupEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<AttendanceGroupMember> listMembers(Long groupId) {
        return memberMapper.selectList(Wrappers.<AttendanceGroupMemberEntity>lambdaQuery()
                        .eq(AttendanceGroupMemberEntity::getGroupId, groupId)
                        .orderByAsc(AttendanceGroupMemberEntity::getUserId))
                .stream().map(AttendanceConfigRepositoryImpl::toMember).toList();
    }

    @Override
    public List<AttendanceGroupMember> listAllMembers() {
        return memberMapper.selectList(Wrappers.<AttendanceGroupMemberEntity>lambdaQuery()
                        .orderByAsc(AttendanceGroupMemberEntity::getGroupId)
                        .orderByAsc(AttendanceGroupMemberEntity::getUserId))
                .stream().map(AttendanceConfigRepositoryImpl::toMember).toList();
    }

    @Override
    public void replaceMembers(Long groupId, List<AttendanceGroupMember> members) {
        memberMapper.deleteByGroupId(groupId);
        for (AttendanceGroupMember member : members) {
            AttendanceGroupMemberEntity entity = new AttendanceGroupMemberEntity();
            entity.setGroupId(groupId);
            entity.setUserId(member.getUserId());
            entity.setUserName(member.getUserName());
            memberMapper.insert(entity);
        }
    }

    @Override
    public List<AttendanceShift> listShifts() {
        return shiftMapper.selectList(Wrappers.<AttendanceShiftEntity>lambdaQuery()
                        .orderByAsc(AttendanceShiftEntity::getCode))
                .stream().map(AttendanceConfigRepositoryImpl::toShift).toList();
    }

    @Override
    public Optional<AttendanceShift> findShift(Long id) {
        return Optional.ofNullable(shiftMapper.selectById(id))
                .map(AttendanceConfigRepositoryImpl::toShift);
    }

    @Override
    public Optional<AttendanceShift> findShiftByCode(String code) {
        return Optional.ofNullable(shiftMapper.selectOne(Wrappers.<AttendanceShiftEntity>lambdaQuery()
                        .eq(AttendanceShiftEntity::getCode, code)))
                .map(AttendanceConfigRepositoryImpl::toShift);
    }

    @Override
    public void saveShift(AttendanceShift shift) {
        AttendanceShiftEntity entity = toShiftEntity(shift);
        if (entity.getId() == null) {
            shiftMapper.insert(entity);
        } else {
            shiftMapper.updateById(entity);
        }
        shift.setId(entity.getId());
    }

    @Override
    public void deleteShift(Long id) {
        shiftMapper.deleteById(id);
    }

    @Override
    public boolean existsShiftCode(String code, Long excludeId) {
        return shiftMapper.selectCount(Wrappers.<AttendanceShiftEntity>lambdaQuery()
                .eq(AttendanceShiftEntity::getCode, code)
                .ne(excludeId != null, AttendanceShiftEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<AttendanceRule> listRules() {
        return ruleMapper.selectList(Wrappers.<AttendanceRuleEntity>lambdaQuery()
                        .orderByAsc(AttendanceRuleEntity::getCode))
                .stream().map(AttendanceConfigRepositoryImpl::toRule).toList();
    }

    @Override
    public Optional<AttendanceRule> findRule(Long id) {
        return Optional.ofNullable(ruleMapper.selectById(id))
                .map(AttendanceConfigRepositoryImpl::toRule);
    }

    @Override
    public Optional<AttendanceRule> findRuleByCode(String code) {
        return Optional.ofNullable(ruleMapper.selectOne(Wrappers.<AttendanceRuleEntity>lambdaQuery()
                        .eq(AttendanceRuleEntity::getCode, code)))
                .map(AttendanceConfigRepositoryImpl::toRule);
    }

    @Override
    public Optional<AttendanceRule> findDefaultRule() {
        return Optional.ofNullable(ruleMapper.selectOne(Wrappers.<AttendanceRuleEntity>lambdaQuery()
                        .eq(AttendanceRuleEntity::getCode, "DEFAULT")))
                .map(AttendanceConfigRepositoryImpl::toRule);
    }

    @Override
    public void saveRule(AttendanceRule rule) {
        AttendanceRuleEntity entity = toRuleEntity(rule);
        if (entity.getId() == null) {
            ruleMapper.insert(entity);
        } else {
            ruleMapper.updateById(entity);
        }
        rule.setId(entity.getId());
    }

    @Override
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    @Override
    public boolean existsRuleCode(String code, Long excludeId) {
        return ruleMapper.selectCount(Wrappers.<AttendanceRuleEntity>lambdaQuery()
                .eq(AttendanceRuleEntity::getCode, code)
                .ne(excludeId != null, AttendanceRuleEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<AttendanceLocation> listLocations() {
        return locationMapper.selectList(Wrappers.<AttendanceLocationEntity>lambdaQuery()
                        .orderByAsc(AttendanceLocationEntity::getCode))
                .stream().map(AttendanceConfigRepositoryImpl::toLocation).toList();
    }

    @Override
    public Optional<AttendanceLocation> findLocation(Long id) {
        return Optional.ofNullable(locationMapper.selectById(id))
                .map(AttendanceConfigRepositoryImpl::toLocation);
    }

    @Override
    public void saveLocation(AttendanceLocation location) {
        AttendanceLocationEntity entity = toLocationEntity(location);
        if (entity.getId() == null) {
            locationMapper.insert(entity);
        } else {
            locationMapper.updateById(entity);
        }
        location.setId(entity.getId());
    }

    @Override
    public void deleteLocation(Long id) {
        locationMapper.deleteById(id);
    }

    @Override
    public boolean existsLocationCode(String code, Long excludeId) {
        return locationMapper.selectCount(Wrappers.<AttendanceLocationEntity>lambdaQuery()
                .eq(AttendanceLocationEntity::getCode, code)
                .ne(excludeId != null, AttendanceLocationEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<AttendanceDevice> listDevices() {
        return deviceMapper.selectList(Wrappers.<AttendanceDeviceEntity>lambdaQuery()
                        .orderByAsc(AttendanceDeviceEntity::getCode))
                .stream().map(AttendanceConfigRepositoryImpl::toDevice).toList();
    }

    @Override
    public Optional<AttendanceDevice> findDevice(Long id) {
        return Optional.ofNullable(deviceMapper.selectById(id))
                .map(AttendanceConfigRepositoryImpl::toDevice);
    }

    @Override
    public Optional<AttendanceDevice> findDeviceByCode(String code) {
        return Optional.ofNullable(deviceMapper.selectOne(Wrappers.<AttendanceDeviceEntity>lambdaQuery()
                        .eq(AttendanceDeviceEntity::getCode, code)))
                .map(AttendanceConfigRepositoryImpl::toDevice);
    }

    @Override
    public void saveDevice(AttendanceDevice device) {
        AttendanceDeviceEntity entity = toDeviceEntity(device);
        if (entity.getId() == null) {
            deviceMapper.insert(entity);
        } else {
            deviceMapper.updateById(entity);
        }
        device.setId(entity.getId());
    }

    @Override
    public void deleteDevice(Long id) {
        deviceMapper.deleteById(id);
    }

    @Override
    public boolean existsDeviceCode(String code, Long excludeId) {
        return deviceMapper.selectCount(Wrappers.<AttendanceDeviceEntity>lambdaQuery()
                .eq(AttendanceDeviceEntity::getCode, code)
                .ne(excludeId != null, AttendanceDeviceEntity::getId, excludeId)) > 0;
    }

    /**
     * 考勤组实体转领域模型。
     *
     * @param entity 考勤组实体
     * @return 考勤组模型
     */
    private static AttendanceGroup toGroup(AttendanceGroupEntity entity) {
        AttendanceGroup model = new AttendanceGroup();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setName(entity.getName());
        model.setShiftId(entity.getShiftId());
        model.setRuleId(entity.getRuleId());
        model.setLocationId(entity.getLocationId());
        model.setDeviceId(entity.getDeviceId());
        model.setEnabled(entity.getEnabled());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 考勤组模型转实体。
     *
     * @param model 考勤组模型
     * @return 考勤组实体
     */
    private static AttendanceGroupEntity toGroupEntity(AttendanceGroup model) {
        AttendanceGroupEntity entity = new AttendanceGroupEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setShiftId(model.getShiftId());
        entity.setRuleId(model.getRuleId());
        entity.setLocationId(model.getLocationId());
        entity.setDeviceId(model.getDeviceId());
        entity.setEnabled(model.getEnabled());
        entity.setRemark(model.getRemark());
        return entity;
    }

    /**
     * 考勤组成员实体转领域模型。
     *
     * @param entity 成员实体
     * @return 成员模型
     */
    private static AttendanceGroupMember toMember(AttendanceGroupMemberEntity entity) {
        AttendanceGroupMember model = new AttendanceGroupMember();
        model.setId(entity.getId());
        model.setGroupId(entity.getGroupId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        return model;
    }

    /**
     * 班次实体转领域模型。
     *
     * @param entity 班次实体
     * @return 班次模型
     */
    private static AttendanceShift toShift(AttendanceShiftEntity entity) {
        AttendanceShift model = new AttendanceShift();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setName(entity.getName());
        model.setWorkStart(entity.getWorkStart());
        model.setWorkEnd(entity.getWorkEnd());
        model.setFlexMinutes(entity.getFlexMinutes());
        model.setBreakMinutes(entity.getBreakMinutes());
        model.setEnabled(entity.getEnabled());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 班次模型转实体。
     *
     * @param model 班次模型
     * @return 班次实体
     */
    private static AttendanceShiftEntity toShiftEntity(AttendanceShift model) {
        AttendanceShiftEntity entity = new AttendanceShiftEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setWorkStart(model.getWorkStart());
        entity.setWorkEnd(model.getWorkEnd());
        entity.setFlexMinutes(model.getFlexMinutes());
        entity.setBreakMinutes(model.getBreakMinutes());
        entity.setEnabled(model.getEnabled());
        entity.setRemark(model.getRemark());
        return entity;
    }

    /**
     * 考勤规则实体转领域模型。
     *
     * @param entity 规则实体
     * @return 规则模型
     */
    private static AttendanceRule toRule(AttendanceRuleEntity entity) {
        AttendanceRule model = new AttendanceRule();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setName(entity.getName());
        model.setAllowExternal(entity.getAllowExternal());
        model.setRequireLocation(entity.getRequireLocation());
        model.setRequireDevice(entity.getRequireDevice());
        model.setMaxCorrectionsPerMonth(entity.getMaxCorrectionsPerMonth());
        model.setCorrectionWindowDays(entity.getCorrectionWindowDays());
        model.setEnabled(entity.getEnabled());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 考勤规则模型转实体。
     *
     * @param model 规则模型
     * @return 规则实体
     */
    private static AttendanceRuleEntity toRuleEntity(AttendanceRule model) {
        AttendanceRuleEntity entity = new AttendanceRuleEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setAllowExternal(model.getAllowExternal());
        entity.setRequireLocation(model.getRequireLocation());
        entity.setRequireDevice(model.getRequireDevice());
        entity.setMaxCorrectionsPerMonth(model.getMaxCorrectionsPerMonth());
        entity.setCorrectionWindowDays(model.getCorrectionWindowDays());
        entity.setEnabled(model.getEnabled());
        entity.setRemark(model.getRemark());
        return entity;
    }

    /**
     * 打卡地点实体转领域模型。
     *
     * @param entity 地点实体
     * @return 地点模型
     */
    private static AttendanceLocation toLocation(AttendanceLocationEntity entity) {
        AttendanceLocation model = new AttendanceLocation();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setName(entity.getName());
        model.setLatitude(entity.getLatitude());
        model.setLongitude(entity.getLongitude());
        model.setRadiusMeters(entity.getRadiusMeters());
        model.setEnabled(entity.getEnabled());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 打卡地点模型转实体。
     *
     * @param model 地点模型
     * @return 地点实体
     */
    private static AttendanceLocationEntity toLocationEntity(AttendanceLocation model) {
        AttendanceLocationEntity entity = new AttendanceLocationEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setLatitude(model.getLatitude());
        entity.setLongitude(model.getLongitude());
        entity.setRadiusMeters(model.getRadiusMeters());
        entity.setEnabled(model.getEnabled());
        entity.setRemark(model.getRemark());
        return entity;
    }

    /**
     * 打卡设备实体转领域模型。
     *
     * @param entity 设备实体
     * @return 设备模型
     */
    private static AttendanceDevice toDevice(AttendanceDeviceEntity entity) {
        AttendanceDevice model = new AttendanceDevice();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setName(entity.getName());
        model.setDeviceType(entity.getDeviceType());
        model.setLocationId(entity.getLocationId());
        model.setEnabled(entity.getEnabled());
        model.setRemark(entity.getRemark());
        return model;
    }

    /**
     * 打卡设备模型转实体。
     *
     * @param model 设备模型
     * @return 设备实体
     */
    private static AttendanceDeviceEntity toDeviceEntity(AttendanceDevice model) {
        AttendanceDeviceEntity entity = new AttendanceDeviceEntity();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setName(model.getName());
        entity.setDeviceType(model.getDeviceType());
        entity.setLocationId(model.getLocationId());
        entity.setEnabled(model.getEnabled());
        entity.setRemark(model.getRemark());
        return entity;
    }
}
