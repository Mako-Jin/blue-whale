package com.bluewhale.oa.attendance.domain.repository;

import com.bluewhale.oa.attendance.domain.model.AttendanceDevice;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroupMember;
import com.bluewhale.oa.attendance.domain.model.AttendanceLocation;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceShift;

import java.util.List;
import java.util.Optional;

/**
 * 考勤主数据仓储端口（班次/规则/地点/设备/考勤组）。
 *
 * @author BlueWhale
 */
public interface AttendanceConfigRepository {

    /**
     * 查询全部考勤组。
     *
     * @return 考勤组列表
     */
    List<AttendanceGroup> listGroups();

    /**
     * 按主键查询考勤组。
     *
     * @param id 主键ID
     * @return 考勤组
     */
    Optional<AttendanceGroup> findGroup(Long id);

    /**
     * 按编码查询考勤组。
     *
     * @param code 考勤组编码
     * @return 考勤组
     */
    Optional<AttendanceGroup> findGroupByCode(String code);

    /**
     * 按用户查询其所属考勤组。
     *
     * @param userId 用户ID
     * @return 考勤组
     */
    Optional<AttendanceGroup> findGroupByUserId(Long userId);

    /**
     * 保存考勤组（新增或更新）。
     *
     * @param group 考勤组
     */
    void saveGroup(AttendanceGroup group);

    /**
     * 逻辑删除考勤组。
     *
     * @param id 主键ID
     */
    void deleteGroup(Long id);

    /**
     * 判断考勤组编码是否已存在。
     *
     * @param code 编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsGroupCode(String code, Long excludeId);

    /**
     * 查询指定考勤组成员。
     *
     * @param groupId 考勤组ID
     * @return 成员列表
     */
    List<AttendanceGroupMember> listMembers(Long groupId);

    /**
     * 列出全部考勤组成员（供日报批量重算使用）。
     *
     * @return 成员列表
     */
    List<AttendanceGroupMember> listAllMembers();

    /**
     * 整组替换考勤组成员。
     *
     * @param groupId 考勤组ID
     * @param members 成员列表
     */
    void replaceMembers(Long groupId, List<AttendanceGroupMember> members);

    /**
     * 查询全部班次。
     *
     * @return 班次列表
     */
    List<AttendanceShift> listShifts();

    /**
     * 按主键查询班次。
     *
     * @param id 主键ID
     * @return 班次
     */
    Optional<AttendanceShift> findShift(Long id);

    /**
     * 按编码查询班次。
     *
     * @param code 班次编码
     * @return 班次
     */
    Optional<AttendanceShift> findShiftByCode(String code);

    /**
     * 保存班次。
     *
     * @param shift 班次
     */
    void saveShift(AttendanceShift shift);

    /**
     * 逻辑删除班次。
     *
     * @param id 主键ID
     */
    void deleteShift(Long id);

    /**
     * 判断班次编码是否已存在。
     *
     * @param code 编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsShiftCode(String code, Long excludeId);

    /**
     * 查询全部考勤规则。
     *
     * @return 规则列表
     */
    List<AttendanceRule> listRules();

    /**
     * 按主键查询考勤规则。
     *
     * @param id 主键ID
     * @return 考勤规则
     */
    Optional<AttendanceRule> findRule(Long id);

    /**
     * 按编码查询考勤规则。
     *
     * @param code 规则编码
     * @return 考勤规则
     */
    Optional<AttendanceRule> findRuleByCode(String code);

    /**
     * 查询默认考勤规则。
     *
     * @return 默认考勤规则
     */
    Optional<AttendanceRule> findDefaultRule();

    /**
     * 保存考勤规则。
     *
     * @param rule 考勤规则
     */
    void saveRule(AttendanceRule rule);

    /**
     * 逻辑删除考勤规则。
     *
     * @param id 主键ID
     */
    void deleteRule(Long id);

    /**
     * 判断考勤规则编码是否已存在。
     *
     * @param code 编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsRuleCode(String code, Long excludeId);

    /**
     * 查询全部打卡地点。
     *
     * @return 地点列表
     */
    List<AttendanceLocation> listLocations();

    /**
     * 按主键查询打卡地点。
     *
     * @param id 主键ID
     * @return 打卡地点
     */
    Optional<AttendanceLocation> findLocation(Long id);

    /**
     * 保存打卡地点。
     *
     * @param location 打卡地点
     */
    void saveLocation(AttendanceLocation location);

    /**
     * 逻辑删除打卡地点。
     *
     * @param id 主键ID
     */
    void deleteLocation(Long id);

    /**
     * 判断打卡地点编码是否已存在。
     *
     * @param code 编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsLocationCode(String code, Long excludeId);

    /**
     * 查询全部打卡设备。
     *
     * @return 设备列表
     */
    List<AttendanceDevice> listDevices();

    /**
     * 按主键查询打卡设备。
     *
     * @param id 主键ID
     * @return 打卡设备
     */
    Optional<AttendanceDevice> findDevice(Long id);

    /**
     * 按编码查询打卡设备。
     *
     * @param code 设备编码
     * @return 打卡设备
     */
    Optional<AttendanceDevice> findDeviceByCode(String code);

    /**
     * 保存打卡设备。
     *
     * @param device 打卡设备
     */
    void saveDevice(AttendanceDevice device);

    /**
     * 逻辑删除打卡设备。
     *
     * @param id 主键ID
     */
    void deleteDevice(Long id);

    /**
     * 判断打卡设备编码是否已存在。
     *
     * @param code 编码
     * @param excludeId 需排除的主键ID
     * @return 是否已存在
     */
    boolean existsDeviceCode(String code, Long excludeId);
}
