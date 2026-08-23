package com.wangning.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.wangning.profile.model.Gender;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 当前用户资料局部更新请求。
 *
 * <p>该对象会记录 JSON 中实际出现的字段，以区分“未提交”与“显式传入 null”。</p>
 */
@Getter
@NoArgsConstructor
public class ProfilePatchRequest {

    private static final int NICKNAME_PRESENT = 1;
    private static final int BIO_PRESENT = 1 << 1;
    private static final int GENDER_PRESENT = 1 << 2;
    private static final int BIRTHDAY_PRESENT = 1 << 3;
    private static final int ZG_ID_PRESENT = 1 << 4;
    private static final int SCHOOL_PRESENT = 1 << 5;
    private static final int TAG_JSON_PRESENT = 1 << 6;

    /** 昵称。 */
    @Size(min = 1, max = 64, message = "昵称长度需在 1-64 之间")
    private String nickname;

    /** 个人简介。 */
    @Size(max = 512, message = "个人简介长度不能超过 512 个字符")
    private String bio;

    /** 性别编码。 */
    private Gender gender;

    /** 生日。 */
    @PastOrPresent(message = "生日不能晚于今天")
    private LocalDate birthday;

    /** 知光号。 */
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{4,32}$",
            message = "知光号仅支持字母、数字、下划线，长度 4-32"
    )
    private String zgId;

    /** 学校或机构名称。 */
    @Size(max = 128, message = "学校名称不能超过 128 个字符")
    private String school;

    /** JSON 字符串数组格式的用户标签。 */
    private String tagJson;

    /** 记录实际出现字段的位掩码，不参与 JSON 读写。 */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    private int presentFields;

    /**
     * 设置昵称并记录字段已出现。
     *
     * @param nickname 昵称，可以为 null，业务层会拒绝清空昵称
     */
    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
        presentFields |= NICKNAME_PRESENT;
    }

    /**
     * 设置个人简介并记录字段已出现。
     *
     * @param bio 个人简介，null 表示清空
     */
    @JsonSetter("bio")
    public void setBio(String bio) {
        this.bio = bio;
        presentFields |= BIO_PRESENT;
    }

    /**
     * 设置性别并记录字段已出现。
     *
     * @param gender 性别，null 表示清空
     */
    @JsonSetter("gender")
    public void setGender(Gender gender) {
        this.gender = gender;
        presentFields |= GENDER_PRESENT;
    }

    /**
     * 设置生日并记录字段已出现。
     *
     * @param birthday 生日，null 表示清空
     */
    @JsonSetter("birthday")
    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
        presentFields |= BIRTHDAY_PRESENT;
    }

    /**
     * 设置知光号并记录字段已出现。
     *
     * @param zgId 知光号，null 表示清空
     */
    @JsonSetter("zgId")
    public void setZgId(String zgId) {
        this.zgId = zgId;
        presentFields |= ZG_ID_PRESENT;
    }

    /**
     * 设置学校并记录字段已出现。
     *
     * @param school 学校或机构名称，null 表示清空
     */
    @JsonSetter("school")
    public void setSchool(String school) {
        this.school = school;
        presentFields |= SCHOOL_PRESENT;
    }

    /**
     * 设置标签 JSON 并记录字段已出现。
     *
     * @param tagJson 标签 JSON，null 表示清空为 []
     */
    @JsonSetter("tagJson")
    public void setTagJson(String tagJson) {
        this.tagJson = tagJson;
        presentFields |= TAG_JSON_PRESENT;
    }

    /**
     * 判断请求是否至少提交了一个可编辑字段。
     *
     * @return 至少一个字段出现时返回 true
     */
    @JsonIgnore
    public boolean hasAnyField() {
        return presentFields != 0;
    }

    /** @return JSON 中是否出现 nickname */
    @JsonIgnore
    public boolean isNicknamePresent() {
        return isPresent(NICKNAME_PRESENT);
    }

    /** @return JSON 中是否出现 bio */
    @JsonIgnore
    public boolean isBioPresent() {
        return isPresent(BIO_PRESENT);
    }

    /** @return JSON 中是否出现 gender */
    @JsonIgnore
    public boolean isGenderPresent() {
        return isPresent(GENDER_PRESENT);
    }

    /** @return JSON 中是否出现 birthday */
    @JsonIgnore
    public boolean isBirthdayPresent() {
        return isPresent(BIRTHDAY_PRESENT);
    }

    /** @return JSON 中是否出现 zgId */
    @JsonIgnore
    public boolean isZgIdPresent() {
        return isPresent(ZG_ID_PRESENT);
    }

    /** @return JSON 中是否出现 school */
    @JsonIgnore
    public boolean isSchoolPresent() {
        return isPresent(SCHOOL_PRESENT);
    }

    /** @return JSON 中是否出现 tagJson */
    @JsonIgnore
    public boolean isTagJsonPresent() {
        return isPresent(TAG_JSON_PRESENT);
    }

    /**
     * 判断指定位是否已经记录。
     *
     * @param fieldMask 字段位掩码
     * @return 字段已出现时返回 true
     */
    private boolean isPresent(int fieldMask) {
        return (presentFields & fieldMask) != 0;
    }
}
