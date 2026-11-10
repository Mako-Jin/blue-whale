package com.bluewhale.contract.converter;

import com.bluewhale.contract.entity.CtContractFlow;
import com.bluewhale.contract.entity.CtContractParty;
import com.bluewhale.contract.entity.CtContractVersion;
import com.bluewhale.contract.vo.ContractFlowVO;
import com.bluewhale.contract.vo.ContractPartyVO;
import com.bluewhale.contract.vo.ContractVersionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 合同模块实体与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。
 * VO 的 id 类字段为 String（对前端规避 Long 精度丢失），由调用方用
 * {@code Objects.toString} 显式转换，故在映射器中声明 ignore。</p>
 * @author BlueWhale
 */
@Mapper
public interface ContractConverter {

    /** 单例（无状态映射器） */
    ContractConverter INSTANCE = Mappers.getMapper(ContractConverter.class);

    /**
     * 合同相对方实体转视图对象（String 型 id 由调用方转换）
     *
     * @param party 相对方实体
     * @return 相对方视图对象
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "signerUserId", ignore = true)
    ContractPartyVO toPartyVo(CtContractParty party);

    /**
     * 合同版本实体转视图对象（String 型 id 由调用方转换）
     *
     * @param version 版本实体
     * @return 版本视图对象
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "fileId", ignore = true)
    ContractVersionVO toVersionVo(CtContractVersion version);

    /**
     * 合同流转记录实体转视图对象（String 型 id 由调用方转换）
     *
     * @param flow 流转记录实体
     * @return 流转记录视图对象
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "versionId", ignore = true)
    @Mapping(target = "processInstanceId", ignore = true)
    @Mapping(target = "definitionId", ignore = true)
    ContractFlowVO toFlowVo(CtContractFlow flow);
}
