package com.fine.service;

/**
 * 企业微信主动推送服务
 */
public interface WeComService {
    /**
     * 发送文本消息
     * @param to 目标 ID (UserID 或 ChatID)
     * @param content 消息内容
     * @return 是否发送成功
     */
    boolean sendTextMessage(String to, String content);

    /**
     * 发送 Markdown 消息
     * @param to 目标 ID
     * @param content Markdown 格式内容
     * @return 是否发送成功
     */
    boolean sendMarkdownMessage(String to, String content);
}
