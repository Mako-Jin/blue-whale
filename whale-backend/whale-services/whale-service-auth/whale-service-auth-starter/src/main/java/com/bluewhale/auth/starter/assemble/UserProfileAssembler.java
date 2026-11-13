package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SaveUserProfileCommand;
import com.bluewhale.auth.application.model.vo.UserProfileVO;
import com.bluewhale.auth.interfaces.dto.request.SaveUserProfileRequest;
import com.bluewhale.auth.interfaces.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 用户身份档案接口装配器：request → command（userId 由路径变量显式注入），view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface UserProfileAssembler {

    /**
     * 保存请求 → 保存命令（命令 userId 以路径变量为准，请求体不携带）
     *
     * @param userId 用户 ID（路径变量）
     * @param request 保存请求
     * @return 保存命令
     */
    default SaveUserProfileCommand toCommand(Long userId, SaveUserProfileRequest request) {
        return new SaveUserProfileCommand(userId, request.getGender(), request.getBirthDate(),
                request.getBirthPlace(), request.getHomeAddress(), request.getNation(),
                request.getPoliticalStatus(), request.getMaritalStatus(), request.getRemarks(),
                toContactItems(request.getContacts()), toCredentialItems(request.getCredentials()),
                toEducationItems(request.getEducations()), toEmploymentItems(request.getEmployments()),
                toEmergencyContactItems(request.getEmergencyContacts()),
                toSecretQuestionItems(request.getSecretQuestions()));
    }

    /**
     * 联系方式条目列表映射
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.ContactItem> toContactItems(List<SaveUserProfileRequest.ContactItem> items);

    /**
     * 证件条目列表映射（证件号明文透传，加密在应用服务）
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.CredentialItem> toCredentialItems(List<SaveUserProfileRequest.CredentialItem> items);

    /**
     * 教育经历条目列表映射
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.EducationItem> toEducationItems(List<SaveUserProfileRequest.EducationItem> items);

    /**
     * 工作经历条目列表映射
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.EmploymentItem> toEmploymentItems(List<SaveUserProfileRequest.EmploymentItem> items);

    /**
     * 紧急联系人条目列表映射
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.EmergencyContactItem> toEmergencyContactItems(
            List<SaveUserProfileRequest.EmergencyContactItem> items);

    /**
     * 密保问题条目列表映射（答案明文透传，加密在应用服务）
     *
     * @param items 请求条目列表
     * @return 命令条目列表
     */
    List<SaveUserProfileCommand.SecretQuestionItem> toSecretQuestionItems(
            List<SaveUserProfileRequest.SecretQuestionItem> items);

    /**
     * 档案视图对象 → 档案响应（脱敏字段对齐透传）
     *
     * @param vo 档案视图对象
     * @return 档案响应
     */
    UserProfileResponse toResponse(UserProfileVO vo);
}
