package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 设备影子视图对象。
 *
 * <p>影子分两段：{@code reported} 为设备最新上报快照，{@code desired} 为平台期望值
 * （由指令下发写入，设备上线/上报后与 reported 比对合并）。{@code version} 每次变更递增，
 * 供设备侧做「谁的数值更新」的判定。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowVO {

    /** 设备ID（字符串化） */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 在线状态：0 离线 / 1 在线 */
    private Integer online;

    /** 最新上报快照（属性标识符 → 取值） */
    private Map<String, Object> reported;

    /** 期望值（属性标识符 → 取值；空表示无待同步项） */
    private Map<String, Object> desired;

    /** 影子版本号（每次变更 +1） */
    private Long version;

    /** 最近上报时间（ISO-8601，可空） */
    private String reportedTime;

    /** 影子更新时间（ISO-8601，可空） */
    private String updatedTime;
}
