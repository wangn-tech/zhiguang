package com.wangning.profile.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.profile.model.Gender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePatchRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldDistinguishMissingFieldsFromExplicitNull() throws Exception {
        ProfilePatchRequest empty = objectMapper.readValue("{}", ProfilePatchRequest.class);
        ProfilePatchRequest clearing = objectMapper.readValue(
                """
                        {
                          "bio": null,
                          "gender": null,
                          "birthday": null,
                          "zgId": null,
                          "school": null,
                          "tagJson": null
                        }
                        """,
                ProfilePatchRequest.class
        );

        assertThat(empty.hasAnyField()).isFalse();
        assertThat(empty.isBioPresent()).isFalse();
        assertThat(clearing.hasAnyField()).isTrue();
        assertThat(clearing.isNicknamePresent()).isFalse();
        assertThat(clearing.isBioPresent()).isTrue();
        assertThat(clearing.isGenderPresent()).isTrue();
        assertThat(clearing.isBirthdayPresent()).isTrue();
        assertThat(clearing.isZgIdPresent()).isTrue();
        assertThat(clearing.isSchoolPresent()).isTrue();
        assertThat(clearing.isTagJsonPresent()).isTrue();
        assertThat(clearing.getBio()).isNull();
        assertThat(clearing.getBirthday()).isNull();
    }

    @Test
    void shouldDeserializeTypedGenderAndBirthday() throws Exception {
        ProfilePatchRequest request = objectMapper.readValue(
                """
                        {
                          "gender": "FEMALE",
                          "birthday": "2000-01-01"
                        }
                        """,
                ProfilePatchRequest.class
        );

        assertThat(request.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(request.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(request.isGenderPresent()).isTrue();
        assertThat(request.isBirthdayPresent()).isTrue();
    }

    @Test
    void shouldValidateProfileFieldConstraints() {
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setNickname("n".repeat(65));
        request.setBio("b".repeat(513));
        request.setZgId("非法知光号");
        request.setSchool("s".repeat(129));
        request.setBirthday(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<ProfilePatchRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "昵称长度需在 1-64 之间",
                        "个人简介长度不能超过 512 个字符",
                        "知光号仅支持字母、数字、下划线，长度 4-32",
                        "学校名称不能超过 128 个字符",
                        "生日不能晚于今天"
                );
    }
}
