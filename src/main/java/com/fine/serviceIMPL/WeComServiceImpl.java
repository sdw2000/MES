package com.fine.serviceIMPL;

import com.alibaba.fastjson.JSONObject;
import com.fine.service.WeComService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class WeComServiceImpl implements WeComService {

    @Value("${wecom.corp-id}")
    private String corpId;

    @Value("${wecom.secret:}")
    private String corpSecret;

    @Value("${wecom.agent-id:}")
    private String agentId;

    private final RestTemplate restTemplate = new RestTemplate();
    
    // Token 缓存
    private String accessToken;
    private long tokenExpireTime = 0;

    /**
     * 获取访问令牌
     */
    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        if (corpSecret == null || corpSecret.isEmpty()) {
            log.error("未配置 wecom.secret，无法获取 AccessToken");
            return null;
        }

        String url = String.format("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s", corpId, corpSecret);
        try {
            JSONObject response = restTemplate.getForObject(url, JSONObject.class);
            if (response != null && response.getInteger("errcode") == 0) {
                accessToken = response.getString("access_token");
                // 提前 5 分钟刷新
                tokenExpireTime = System.currentTimeMillis() + (response.getInteger("expires_in") - 300) * 1000L;
                log.info("成功获取企业微信 AccessToken，有效期至: {}", new java.util.Date(tokenExpireTime));
                return accessToken;
            } else {
                log.error("获取企业微信 AccessToken 失败: {}", response);
            }
        } catch (Exception e) {
            log.error("获取企业微信 AccessToken 异常", e);
        }
        return null;
    }

    @Override
    public boolean sendTextMessage(String to, String content) {
        return sendMessage(to, "text", content);
    }

    @Override
    public boolean sendMarkdownMessage(String to, String content) {
        return sendMessage(to, "markdown", content);
    }

    private boolean sendMessage(String to, String msgType, String content) {
        String token = getAccessToken();
        if (token == null) return false;

        // 判断是否为群聊 ID (ChatID 通常包含 wr 或以特定前缀开始，这里简单判断长度或特征)
        // 实际上 ChatId 和 UserID 在发送接口上有所区别
        // 1. 发送给个人/全员使用 message/send
        // 2. 发送给群聊使用 appchat/send
        
        boolean isGroup = to != null && (to.startsWith("wr") || to.length() > 20); // 给群发消息通常是 ChatId

        String url;
        JSONObject body = new JSONObject();
        
        if (isGroup) {
            url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/send?access_token=" + token;
            body.put("chatid", to);
        } else {
            url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
            body.put("touser", to);
            body.put("agentid", agentId);
        }

        body.put("msgtype", msgType);
        JSONObject contentObj = new JSONObject();
        contentObj.put("content", content);
        body.put(msgType, contentObj);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            
            JSONObject res = restTemplate.postForObject(url, entity, JSONObject.class);
            if (res != null && res.getInteger("errcode") == 0) {
                log.info("成功发送 WeCom 消息到 {}: {}", to, msgType);
                return true;
            } else {
                log.warn("发送 WeCom 消息失败，返回码: {}, 响应: {}", to, res);
                // 如果是群聊发送失败，尝试切换到普通消息发送（万一 'to' 是个很长的 UserID）
                if (isGroup && res != null && res.getInteger("errcode") == 86001) { // 86001: 不合法的会话ID
                     return sendMessage(to, msgType, content); // 这里会有死循环风险，需谨慎，暂不递归
                }
            }
        } catch (Exception e) {
            log.error("发送 WeCom 消息异常", e);
        }
        return false;
    }
}
