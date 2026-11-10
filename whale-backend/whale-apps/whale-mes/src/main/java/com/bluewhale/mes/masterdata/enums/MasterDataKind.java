package com.bluewhale.mes.masterdata.enums;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * MES 车间主数据类型
 *
 * @author BlueWhale
 */
@Getter
@AllArgsConstructor
public enum MasterDataKind {

    /** 车间（自身无 workshop_id 列，不参与车间维度过滤） */
    WORKSHOP("workshop", "车间", false, false, false),

    /** 产线 */
    LINE("line", "产线", true, false, false),

    /** 工作中心 */
    WORK_CENTER("work-center", "工作中心", true, true, false),

    /** 工位 */
    STATION("station", "工位", false, true, true),

    /** 班组 */
    TEAM("team", "班组", true, false, false),

    /** 班次 */
    SHIFT("shift", "班次", false, false, false),

    /** 工序 */
    OPERATION("operation", "工序", false, false, false),

    /** 缺陷代码 */
    DEFECT_CODE("defect-code", "缺陷代码", false, false, false),

    /** 异常/停机原因 */
    REASON_CODE("reason-code", "异常原因", false, false, false);

    /** 路径标识 */
    private final String path;

    /** 中文名称 */
    private final String label;

    /** 是否支持车间维度 */
    private final boolean workshopDimension;

    /** 是否支持产线维度 */
    private final boolean lineDimension;

    /** 是否支持工作中心维度 */
    private final boolean workCenterDimension;

    /**
     * 解析路径标识
     *
     * @param path 路径标识
     * @return 主数据类型
     */
    public static MasterDataKind fromPath(String path) {
        return Arrays.stream(values())
                .filter(kind -> kind.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "不支持的主数据类型：" + path));
    }
}
