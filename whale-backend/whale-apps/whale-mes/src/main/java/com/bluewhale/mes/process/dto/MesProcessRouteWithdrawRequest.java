package com.bluewhale.mes.process.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * MES 撤回工艺路线请求
 *
 * <p>契约来源：{@code POST /mes/internal/process-route/withdraw}。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesProcessRouteWithdrawRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** PDM 工艺路线编号 */
    @NotBlank(message = "{validation.mes.mes-process-route-withdraw.route-no-required}")
    private String routeNo;

    /** PDM 工艺路线版本号 */
    @NotBlank(message = "{validation.mes.mes-process-route-withdraw.version-no-required}")
    private String versionNo;
}
