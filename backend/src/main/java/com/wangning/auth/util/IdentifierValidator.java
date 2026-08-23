package com.wangning.auth.util;

import java.util.regex.Pattern;

/**
 * 手机号和邮箱格式校验工具。
 *
 * <p>调用方应先完成去除首尾空格、邮箱转小写等标准化操作。</p>
 */
public final class IdentifierValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private IdentifierValidator() {
    }

    /**
     * 判断是否为有效的中国大陆手机号。
     *
     * @param phone 待校验手机号
     * @return 格式正确时返回 {@code true}
     */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 判断是否为有效邮箱。
     *
     * @param email 待校验邮箱
     * @return 格式正确时返回 {@code true}
     */
    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 128 && EMAIL_PATTERN.matcher(email).matches();
    }
}
