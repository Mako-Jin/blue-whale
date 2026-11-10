package com.bluewhale.mes.masterdata.converter;

import com.bluewhale.mes.masterdata.entity.MesDefectCode;
import com.bluewhale.mes.masterdata.entity.MesMasterDataEntity;
import com.bluewhale.mes.masterdata.entity.MesOperation;
import com.bluewhale.mes.masterdata.entity.MesProductionLine;
import com.bluewhale.mes.masterdata.entity.MesReasonCode;
import com.bluewhale.mes.masterdata.entity.MesShift;
import com.bluewhale.mes.masterdata.entity.MesStation;
import com.bluewhale.mes.masterdata.entity.MesTeam;
import com.bluewhale.mes.masterdata.entity.MesWorkCenter;
import com.bluewhale.mes.masterdata.entity.MesWorkshop;
import com.bluewhale.mes.masterdata.vo.MesMasterDataVO;
import org.mapstruct.Mapper;

/**
 * MES 车间主数据转换器
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface MesMasterDataConverter {

    /**
     * 车间转视图
     *
     * @param entity 车间实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesWorkshop entity);

    /**
     * 产线转视图
     *
     * @param entity 产线实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesProductionLine entity);

    /**
     * 工作中心转视图
     *
     * @param entity 工作中心实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesWorkCenter entity);

    /**
     * 工位转视图
     *
     * @param entity 工位实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesStation entity);

    /**
     * 班组转视图
     *
     * @param entity 班组实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesTeam entity);

    /**
     * 班次转视图
     *
     * @param entity 班次实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesShift entity);

    /**
     * 工序转视图
     *
     * @param entity 工序实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesOperation entity);

    /**
     * 缺陷代码转视图
     *
     * @param entity 缺陷代码实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesDefectCode entity);

    /**
     * 异常原因转视图
     *
     * @param entity 异常原因实体
     * @return 主数据视图
     */
    MesMasterDataVO toVo(MesReasonCode entity);

    /**
     * 按运行时类型转视图
     *
     * @param entity 主数据实体
     * @return 主数据视图
     */
    default MesMasterDataVO toVo(MesMasterDataEntity entity) {
        if (entity instanceof MesWorkshop workshop) {
            return toVo(workshop);
        }
        if (entity instanceof MesProductionLine line) {
            return toVo(line);
        }
        if (entity instanceof MesWorkCenter workCenter) {
            return toVo(workCenter);
        }
        if (entity instanceof MesStation station) {
            return toVo(station);
        }
        if (entity instanceof MesTeam team) {
            return toVo(team);
        }
        if (entity instanceof MesShift shift) {
            return toVo(shift);
        }
        if (entity instanceof MesOperation operation) {
            return toVo(operation);
        }
        if (entity instanceof MesDefectCode defectCode) {
            return toVo(defectCode);
        }
        if (entity instanceof MesReasonCode reasonCode) {
            return toVo(reasonCode);
        }
        throw new IllegalArgumentException("不支持的主数据实体：" + entity.getClass().getName());
    }
}
