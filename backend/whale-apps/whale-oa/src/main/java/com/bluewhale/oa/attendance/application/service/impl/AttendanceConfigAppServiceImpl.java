package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceConfigAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceDevice;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroupMember;
import com.bluewhale.oa.attendance.domain.model.AttendanceLocation;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import com.bluewhale.oa.attendance.domain.repository.AttendanceConfigRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考勤主数据应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceConfigAppServiceImpl implements AttendanceConfigAppService {

    private final AttendanceConfigRepository configRepository;

    @Override
    public List<AttendanceViews.GroupView> listGroups() {
        return configRepository.listGroups().stream()
                .map(group -> AttendanceViews.GroupView.from(group, configRepository.listMembers(group.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.GroupView saveGroup(Long id, AttendanceCommands.GroupSaveCommand command) {
        AttendanceGroup group = id == null ? new AttendanceGroup() : configRepository.findGroup(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        if (configRepository.existsGroupCode(command.getCode(), id)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CONFIG_EXISTS);
        }
        configRepository.findShift(command.getShiftId())
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_SHIFT_NOT_FOUND));
        ensureOptionalExists(command.getRuleId(), "规则", configRepository::findRule);
        ensureOptionalExists(command.getLocationId(), "地点", configRepository::findLocation);
        ensureOptionalExists(command.getDeviceId(), "设备", configRepository::findDevice);
        group.setCode(command.getCode());
        group.setName(command.getName());
        group.setShiftId(command.getShiftId());
        group.setRuleId(command.getRuleId());
        group.setLocationId(command.getLocationId());
        group.setDeviceId(command.getDeviceId());
        group.setEnabled(command.getEnabled() == null ? 1 : command.getEnabled());
        group.setRemark(command.getRemark());
        configRepository.saveGroup(group);
        if (command.getMembers() != null) {
            List<AttendanceGroupMember> members = command.getMembers().stream().map(member -> {
                AttendanceGroupMember model = new AttendanceGroupMember();
                model.setUserId(member.getUserId());
                model.setUserName(member.getUserName());
                return model;
            }).toList();
            configRepository.replaceMembers(group.getId(), members);
        }
        return AttendanceViews.GroupView.from(group, configRepository.listMembers(group.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        configRepository.findGroup(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        configRepository.deleteGroup(id);
    }

    @Override
    public List<AttendanceViews.ShiftView> listShifts() {
        return configRepository.listShifts().stream().map(AttendanceViews.ShiftView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.ShiftView saveShift(Long id, AttendanceCommands.ShiftSaveCommand command) {
        AttendanceShift shift = id == null ? new AttendanceShift()
                : configRepository.findShift(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        if (configRepository.existsShiftCode(command.getCode(), id)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CONFIG_EXISTS);
        }
        shift.setCode(command.getCode());
        shift.setName(command.getName());
        shift.setWorkStart(command.getWorkStart());
        shift.setWorkEnd(command.getWorkEnd());
        shift.setFlexMinutes(nonNegative(command.getFlexMinutes(), 0));
        shift.setBreakMinutes(nonNegative(command.getBreakMinutes(), 60));
        shift.setEnabled(command.getEnabled() == null ? 1 : command.getEnabled());
        shift.setRemark(command.getRemark());
        configRepository.saveShift(shift);
        return AttendanceViews.ShiftView.from(shift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShift(Long id) {
        configRepository.findShift(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_SHIFT_NOT_FOUND));
        configRepository.deleteShift(id);
    }

    @Override
    public List<AttendanceViews.RuleView> listRules() {
        return configRepository.listRules().stream().map(AttendanceViews.RuleView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.RuleView saveRule(Long id, AttendanceCommands.RuleSaveCommand command) {
        AttendanceRule rule = id == null ? new AttendanceRule()
                : configRepository.findRule(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        if (configRepository.existsRuleCode(command.getCode(), id)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CONFIG_EXISTS);
        }
        rule.setCode(command.getCode());
        rule.setName(command.getName());
        rule.setAllowExternal(flag(command.getAllowExternal(), 1));
        rule.setRequireLocation(flag(command.getRequireLocation(), 0));
        rule.setRequireDevice(flag(command.getRequireDevice(), 0));
        rule.setMaxCorrectionsPerMonth(nonNegative(command.getMaxCorrectionsPerMonth(), 3));
        rule.setCorrectionWindowDays(nonNegative(command.getCorrectionWindowDays(), 7));
        rule.setEnabled(command.getEnabled() == null ? 1 : command.getEnabled());
        rule.setRemark(command.getRemark());
        configRepository.saveRule(rule);
        return AttendanceViews.RuleView.from(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        configRepository.findRule(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        configRepository.deleteRule(id);
    }

    @Override
    public List<AttendanceViews.LocationView> listLocations() {
        return configRepository.listLocations().stream().map(AttendanceViews.LocationView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.LocationView saveLocation(Long id, AttendanceCommands.LocationSaveCommand command) {
        AttendanceLocation location = id == null ? new AttendanceLocation()
                : configRepository.findLocation(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        if (configRepository.existsLocationCode(command.getCode(), id)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CONFIG_EXISTS);
        }
        ensureCoordinate(command.getLatitude(), command.getLongitude());
        location.setCode(command.getCode());
        location.setName(command.getName());
        location.setLatitude(command.getLatitude());
        location.setLongitude(command.getLongitude());
        location.setRadiusMeters(nonNegative(command.getRadiusMeters(), 300));
        location.setEnabled(command.getEnabled() == null ? 1 : command.getEnabled());
        location.setRemark(command.getRemark());
        configRepository.saveLocation(location);
        return AttendanceViews.LocationView.from(location);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(Long id) {
        configRepository.findLocation(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        configRepository.deleteLocation(id);
    }

    @Override
    public List<AttendanceViews.DeviceView> listDevices() {
        return configRepository.listDevices().stream().map(AttendanceViews.DeviceView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.DeviceView saveDevice(Long id, AttendanceCommands.DeviceSaveCommand command) {
        AttendanceDevice device = id == null ? new AttendanceDevice()
                : configRepository.findDevice(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        if (configRepository.existsDeviceCode(command.getCode(), id)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CONFIG_EXISTS);
        }
        ensureOptionalExists(command.getLocationId(), "地点", configRepository::findLocation);
        device.setCode(command.getCode());
        device.setName(command.getName());
        device.setDeviceType(StringUtils.hasText(command.getDeviceType()) ? command.getDeviceType() : "OTHER");
        device.setLocationId(command.getLocationId());
        device.setEnabled(command.getEnabled() == null ? 1 : command.getEnabled());
        device.setRemark(command.getRemark());
        configRepository.saveDevice(device);
        return AttendanceViews.DeviceView.from(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        configRepository.findDevice(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND));
        configRepository.deleteDevice(id);
    }

    /**
     * 校验经纬度取值区间。
     *
     * @param latitude 纬度
     * @param longitude 经度
     */
    private void ensureCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "纬度必须在 -90 到 90 之间");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "经度必须在 -180 到 180 之间");
        }
    }

    /**
     * 校验可选关联配置是否存在。
     *
     * @param id 关联主键
     * @param label 关联对象名称
     * @param finder 查询函数
     * @param <T> 关联对象类型
     */
    private <T> void ensureOptionalExists(Long id, String label,
                                          java.util.function.Function<Long, java.util.Optional<T>> finder) {
        if (id != null) {
            finder.apply(id).orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CONFIG_NOT_FOUND,
                    label + "不存在"));
        }
    }

    /**
     * 校验非负整数并回填默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 校验后的值
     */
    private static int nonNegative(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 0) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "数值不能为负");
        }
        return value;
    }

    /**
     * 归一化 0/1 标志位。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 归一化标志位
     */
    private static int flag(Integer value, int defaultValue) {
        return value == null ? defaultValue : (value == 0 ? 0 : 1);
    }
}
