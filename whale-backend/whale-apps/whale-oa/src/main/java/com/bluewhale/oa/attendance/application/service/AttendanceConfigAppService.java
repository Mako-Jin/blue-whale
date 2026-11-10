package com.bluewhale.oa.attendance.application.service;

import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;

import java.util.List;

/**
 * 考勤主数据应用服务。
 *
 * @author BlueWhale
 */
public interface AttendanceConfigAppService {

    /**
     * 查询全部考勤组（含成员）。
     *
     * @return 考勤组列表
     */
    List<AttendanceViews.GroupView> listGroups();

    /**
     * 新增或更新考勤组。
     *
     * @param id 主键，新增传 {@code null}
     * @param command 保存命令
     * @return 保存后的考勤组
     */
    AttendanceViews.GroupView saveGroup(Long id, AttendanceCommands.GroupSaveCommand command);

    /**
     * 删除考勤组。
     *
     * @param id 主键
     */
    void deleteGroup(Long id);

    /**
     * 查询全部班次。
     *
     * @return 班次列表
     */
    List<AttendanceViews.ShiftView> listShifts();

    /**
     * 新增或更新班次。
     *
     * @param id 主键，新增传 {@code null}
     * @param command 保存命令
     * @return 保存后的班次
     */
    AttendanceViews.ShiftView saveShift(Long id, AttendanceCommands.ShiftSaveCommand command);

    /**
     * 删除班次。
     *
     * @param id 主键
     */
    void deleteShift(Long id);

    /**
     * 查询全部规则。
     *
     * @return 规则列表
     */
    List<AttendanceViews.RuleView> listRules();

    /**
     * 新增或更新规则。
     *
     * @param id 主键，新增传 {@code null}
     * @param command 保存命令
     * @return 保存后的规则
     */
    AttendanceViews.RuleView saveRule(Long id, AttendanceCommands.RuleSaveCommand command);

    /**
     * 删除规则。
     *
     * @param id 主键
     */
    void deleteRule(Long id);

    /**
     * 查询全部打卡地点。
     *
     * @return 地点列表
     */
    List<AttendanceViews.LocationView> listLocations();

    /**
     * 新增或更新打卡地点。
     *
     * @param id 主键，新增传 {@code null}
     * @param command 保存命令
     * @return 保存后的地点
     */
    AttendanceViews.LocationView saveLocation(Long id, AttendanceCommands.LocationSaveCommand command);

    /**
     * 删除打卡地点。
     *
     * @param id 主键
     */
    void deleteLocation(Long id);

    /**
     * 查询全部打卡设备。
     *
     * @return 设备列表
     */
    List<AttendanceViews.DeviceView> listDevices();

    /**
     * 新增或更新打卡设备。
     *
     * @param id 主键，新增传 {@code null}
     * @param command 保存命令
     * @return 保存后的设备
     */
    AttendanceViews.DeviceView saveDevice(Long id, AttendanceCommands.DeviceSaveCommand command);

    /**
     * 删除打卡设备。
     *
     * @param id 主键
     */
    void deleteDevice(Long id);
}
