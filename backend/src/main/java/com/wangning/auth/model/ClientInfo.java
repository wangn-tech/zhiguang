package com.wangning.auth.model;

/**
 * 发起认证请求的客户端信息。
 *
 * @param ip 客户端 IP
 * @param userAgent User-Agent 请求头
 */
public record ClientInfo(String ip, String userAgent) {
}
