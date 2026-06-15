package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.Customer;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.modle.SalesOrder;
import com.fine.service.DeliveryNoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Clean and working WeCom Controller
 */
@RestController
@RequestMapping("/wecom")
public class WeComController {

    private static final Logger log = LoggerFactory.getLogger(WeComController.class);

    @Autowired
    private DeliveryNoticeService deliveryNoticeService;

    @Autowired
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Value("${wecom.token:SVAiEyC2X43WTLtMQ5fS1jOsPge7Xpg}")
    private String token;

    @Value("${wecom.encoding-aes-key:99HAX4wweXVmH3JlmutHqGAU2qSIssLhyffuQm9GeYm}")
    private String encodingAesKey;

    @Value("${wecom.corp-id:wwbf9c3ec52c95c869}")
    private String corpId;

    @Value("${wecom.secret:}")
    private String corpSecret;

    @Value("${wecom.agent-id:}")
    private String agentId;

    @GetMapping(value = "/callback")
    public org.springframework.http.ResponseEntity<String> verifyUrl(
            @RequestParam(value = "msg_signature", required = false) String msgSignature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "echostr", required = false) String echostr) {
        
        if (msgSignature == null && echostr == null) {
            String html = "<html><body><h2>WeCom ID Tool</h2><p>Visit /wecom/groups for group list</p></body></html>";
            return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML).body(html);
        }

        try {
            if (!verifySignature(token, timestamp, nonce, echostr, msgSignature)) return org.springframework.http.ResponseEntity.ok("error");
            String result = decrypt(echostr, encodingAesKey);
            return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(result);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.ok("error");
        }
    }

    @GetMapping(value = "/groups")
    public org.springframework.http.ResponseEntity<String> listExternalChats() {
        try {
            String tokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;
            String tokenRes = restTemplateGet(tokenUrl);
            com.alibaba.fastjson.JSONObject tokenJson = com.alibaba.fastjson.JSON.parseObject(tokenRes);
            String accessToken = tokenJson.getString("access_token");
            if (accessToken == null) return org.springframework.http.ResponseEntity.ok("Token Error: " + tokenRes);

            String listUrl = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/groupchat/list?access_token=" + accessToken;
            String listRes = restTemplatePost(listUrl, "{\"limit\":100}");
            com.alibaba.fastjson.JSONObject listObj = com.alibaba.fastjson.JSON.parseObject(listRes);
            com.alibaba.fastjson.JSONArray groupList = listObj.getJSONArray("group_chat_list");
            
            if (groupList == null || groupList.isEmpty()) {
                return org.springframework.http.ResponseEntity.ok("<html><body><h2>未找到群聊</h2>" + 
                    "<p>接口返回内容: " + listRes + "</p>" +
                    "<p><b>排查建议：</b><br>1. 请检查企微后台 <b>客户联系 -> API -> 可调用应用</b> 中是否勾选了本应用。<br>" +
                    "2. 如果返回 errcode: 81011，说明无业务权限，请确认您使用的是‘客户联系’面板下的 Secret。</p></body></html>");
            }

            StringBuilder html = new StringBuilder("<html><body><h2>Group List</h2><table border='1'><tr><th>Name</th><th>ID</th></tr>");
            for (int i = 0; i < groupList.size(); i++) {
                String chatId = groupList.getJSONObject(i).getString("chat_id");
                String detailUrl = "https://qyapi.weixin.qq.com/cgi-bin/externalcontact/groupchat/get?access_token=" + accessToken;
                String detailRes = restTemplatePost(detailUrl, "{\"chat_id\":\"" + chatId + "\"}");
                com.alibaba.fastjson.JSONObject detailObj = com.alibaba.fastjson.JSON.parseObject(detailRes);
                String name = "Unknown";
                if (detailObj != null && detailObj.getJSONObject("group_chat") != null) {
                    name = detailObj.getJSONObject("group_chat").getString("group_name");
                }
                html.append("<tr><td>").append(name).append("</td><td>").append(chatId).append("</td></tr>");
            }
            html.append("</table></body></html>");
            return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML).body(html.toString());
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.ok("Error: " + e.getMessage());
        }
    }

    public void sendAutoShipmentNotification(String chatId, DeliveryNotice notice) {
        try {
            String tokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;
            String tokenRes = restTemplateGet(tokenUrl);
            String accessToken = com.alibaba.fastjson.JSON.parseObject(tokenRes).getString("access_token");

            String rawCode = notice.getCustomer() + "|" + notice.getNoticeNo();
            String obsCode = Base64.getUrlEncoder().encodeToString(rawCode.getBytes(StandardCharsets.UTF_8)).replace("=", "");
            String viewUrl = "http://erp.fine-mes.com/shipment/view?code=" + obsCode;

            String content = "📦 【发货通知】\n" +
                           "客户：" + notice.getCustomer() + "\n" +
                           "单号：" + notice.getNoticeNo() + "\n" +
                           "时间：" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) + "\n\n" +
                           "您可以点击下方链接查看详细物流与清单：\n" + viewUrl;

            String pushUrl = "https://qyapi.weixin.qq.com/cgi-bin/appchat/send?access_token=" + accessToken;
            Map<String, Object> body = new HashMap<>();
            body.put("chatid", chatId);
            body.put("msgtype", "text");
            Map<String, String> text = new HashMap<>();
            text.put("content", content);
            body.put("text", text);
            body.put("safe", 0);

            restTemplatePost(pushUrl, com.alibaba.fastjson.JSON.toJSONString(body));
            log.info("企微消息已推送到群: {}", chatId);
        } catch (Exception e) {
            log.error("推送发货通知失败", e);
        }
    }

    private String restTemplateGet(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        return readStream(conn.getInputStream());
    }

    private String restTemplatePost(String url, String body) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.getOutputStream().write(body.getBytes("UTF-8"));
        return readStream(conn.getInputStream());
    }

    private String readStream(java.io.InputStream is) throws Exception {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    @PostMapping(value = "/callback", produces = "application/xml; charset=UTF-8")
    public String handleMessage(
            @RequestParam(value = "msg_signature", required = false) String msgSignature,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestBody String body) {
        
        try {
            // 1. 从 XML 中提取加密内容
            String encrypt = extractXmlTag(body, "Encrypt");
            if (encrypt == null) return "success";

            // 2. 解密
            String decryptedXml = decrypt(encrypt, encodingAesKey);
            log.info("收到企微消息密文解密成功");

            // 3. 解析消息内容
            String msgType = extractXmlTag(decryptedXml, "MsgType");
            String fromUser = extractXmlTag(decryptedXml, "FromUserName");
            String toUser = extractXmlTag(decryptedXml, "ToUserName");

            if ("text".equals(msgType)) {
                String content = extractXmlTag(decryptedXml, "Content");
                log.info("企微消息内容: {}", content);
                
                String responseText = "未找到相关单号信息。";
                
                // 处理查询逻辑
                if (content != null) {
                    content = content.trim();
                    DeliveryNotice notice = null;

                    if (content.startsWith("Fine")) {
                        // 按发货单号查
                        List<DeliveryNotice> notices = deliveryNoticeMapper.selectList(new QueryWrapper<DeliveryNotice>().eq("notice_no", content).eq("is_deleted", 0));
                        if (!notices.isEmpty()) notice = notices.get(0);
                    } else if (content.toUpperCase().startsWith("DH") || content.length() > 6) {
                        // 按销售订单号查
                        List<DeliveryNotice> notices = deliveryNoticeMapper.selectList(new QueryWrapper<DeliveryNotice>().eq("order_no", content).eq("is_deleted", 0).orderByDesc("id"));
                        if (!notices.isEmpty()) notice = notices.get(0);
                    } else {
                        // 可能是客户代码，查该客户最新的一单
                        List<DeliveryNotice> notices = deliveryNoticeMapper.selectList(new QueryWrapper<DeliveryNotice>().eq("customer", content).eq("is_deleted", 0).orderByDesc("id").last("LIMIT 1"));
                        if (!notices.isEmpty()) notice = notices.get(0);
                    }

                    if (notice != null) {
                        responseText = formatNoticeResponse(notice);
                    }
                }

                // 4. 构造响应 XML
                String replyXml = String.format(
                    "<xml><ToUserName><![CDATA[%s]]></ToUserName><FromUserName><![CDATA[%s]]></FromUserName><CreateTime>%d</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[%s]]></Content></xml>",
                    fromUser, toUser, System.currentTimeMillis() / 1000, responseText
                );

                // 5. 加密响应
                String encryptedReply = encrypt(replyXml, encodingAesKey);
                
                // 6. 生成签名
                String replySignature = generateSignature(token, timestamp, nonce, encryptedReply);

                return String.format(
                    "<xml><Encrypt><![CDATA[%s]]></Encrypt><MsgSignature><![CDATA[%s]]></MsgSignature><TimeStamp>%s</TimeStamp><Nonce><![CDATA[%s]]></Nonce></xml>",
                    encryptedReply, replySignature, timestamp, nonce
                );
            }
        } catch (Exception e) {
            log.error("处理企微回调消息失败", e);
        }
        return "success";
    }

    private String formatNoticeResponse(DeliveryNotice notice) {
        StringBuilder sb = new StringBuilder();
        sb.append("📦 【发货单助手】\n");
        sb.append("发货单号：").append(notice.getNoticeNo()).append("\n");
        sb.append("销售单号：").append(notice.getOrderNo() != null ? notice.getOrderNo() : "无").append("\n");
        sb.append("日期：").append(notice.getDeliveryDate() != null ? notice.getDeliveryDate().toString() : "未设置").append("\n");
        
        String logistics = "暂无信息";
        if (notice.getCarrierNo() != null && !notice.getCarrierNo().isEmpty()) {
            logistics = (notice.getCarrierName() != null ? notice.getCarrierName() : "物流") + " [" + notice.getCarrierNo() + "]";
        }
        sb.append("物流：").append(logistics).append("\n");

        // 详情链接
        String rawCode = "noticeNo=" + notice.getNoticeNo() + "&customer=" + notice.getCustomer();
        String obsCode = Base64.getUrlEncoder().encodeToString(rawCode.getBytes(StandardCharsets.UTF_8)).replace("=", "");
        String viewUrl = "https://api.maxtritape.com/#/public-shipment?p=" + obsCode;
        
        sb.append("\n👉 [更多详情查看(免登录)](").append(viewUrl).append(")");
        
        return sb.toString();
    }

    /**
     * 公开查询 API：供免登录页面调用
     */
    @GetMapping("/public/shipments")
    public ResponseResult<List<DeliveryNotice>> getPublicShipments(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String noticeNo,
            @RequestParam(required = false) String p) {
        
        String finalCustomer = customer;
        String finalNoticeNo = noticeNo;

        // 如果传了 p，优先解析 p
        if (p != null && !p.isEmpty()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(p.replace("-", "+").replace("_", "/")), StandardCharsets.UTF_8);
                String[] pairs = decoded.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        if ("customer".equals(kv[0])) finalCustomer = kv[1];
                        else if ("noticeNo".equals(kv[0])) finalNoticeNo = kv[1];
                    }
                }
            } catch (Exception e) {
                log.error("解析公开查询参数失败", e);
            }
        }

        if (finalCustomer == null && finalNoticeNo == null) {
            return ResponseResult.error("缺少查询参数");
        }

        QueryWrapper<DeliveryNotice> wrapper = new QueryWrapper<>();
        if (finalCustomer != null) wrapper.eq("customer", finalCustomer);
        if (finalNoticeNo != null) wrapper.eq("notice_no", finalNoticeNo);
        wrapper.eq("is_deleted", 0).orderByDesc("id");
        
        List<DeliveryNotice> notices = deliveryNoticeMapper.selectList(wrapper);
        for (DeliveryNotice notice : notices) {
            // 加载详情项
            List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectList(
                new QueryWrapper<DeliveryNoticeItem>().eq("notice_id", notice.getId())
            );
            notice.setItems(items);
        }

        return ResponseResult.success(notices);
    }

    private String extractXmlTag(String xml, String tag) {
        if (xml == null) return null;
        int start = xml.indexOf("<" + tag + ">");
        if (start == -1) return null;
        start += tag.length() + 2;
        if (xml.indexOf("<![CDATA[", start) == start) {
            start += 9;
            int end = xml.indexOf("]]>", start);
            return (end == -1) ? null : xml.substring(start, end);
        } else {
            int end = xml.indexOf("</" + tag + ">", start);
            return (end == -1) ? null : xml.substring(start, end);
        }
    }

    private String encrypt(String replyXml, String encodingAesKey) throws Exception {
        byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        ByteBuf buf = new ByteBuf();
        buf.addBytes(getRandomStr().getBytes(StandardCharsets.UTF_8));
        
        byte[] msgBytes = replyXml.getBytes(StandardCharsets.UTF_8);
        byte[] networkOrderLength = getNetworkOrderLength(msgBytes.length);
        buf.addBytes(networkOrderLength);
        buf.addBytes(msgBytes);
        buf.addBytes(corpId.getBytes(StandardCharsets.UTF_8));
        
        byte[] padBytes = PKCS7Encoder.encode(buf.getSize());
        buf.addBytes(padBytes);
        
        byte[] unencrypted = buf.toBytes();
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        byte[] encrypted = cipher.doFinal(unencrypted);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String getRandomStr() {
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(base.charAt(random.nextInt(base.length())));
        return sb.toString();
    }

    private byte[] getNetworkOrderLength(int length) {
        byte[] orderBytes = new byte[4];
        orderBytes[3] = (byte) (length & 0xFF);
        orderBytes[2] = (byte) ((length >> 8) & 0xFF);
        orderBytes[1] = (byte) ((length >> 16) & 0xFF);
        orderBytes[0] = (byte) ((length >> 24) & 0xFF);
        return orderBytes;
    }

    private String generateSignature(String token, String timestamp, String nonce, String encryptedMsg) {
        try {
            String[] array = new String[]{token, timestamp, nonce, encryptedMsg};
            Arrays.sort(array);
            StringBuilder sb = new StringBuilder();
            for (String str : array) sb.append(str);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes());
            StringBuilder hexStr = new StringBuilder();
            for (byte b : digest) {
                String shaHex = Integer.toHexString(b & 0xFF);
                if (shaHex.length() < 2) hexStr.append(0);
                hexStr.append(shaHex);
            }
            return hexStr.toString();
        } catch (Exception e) { return ""; }
    }

    static class ByteBuf {
        private List<byte[]> list = new ArrayList<>();
        private int size = 0;
        public void addBytes(byte[] bytes) { list.add(bytes); size += bytes.length; }
        public int getSize() { return size; }
        public byte[] toBytes() {
            byte[] all = new byte[size];
            int pos = 0;
            for (byte[] b : list) { System.arraycopy(b, 0, all, pos, b.length); pos += b.length; }
            return all;
        }
    }

    static class PKCS7Encoder {
        static int BLOCK_SIZE = 32;
        static byte[] encode(int count) {
            int amountToPad = BLOCK_SIZE - (count % BLOCK_SIZE);
            if (amountToPad == 0) amountToPad = BLOCK_SIZE;
            byte pad = (byte) (amountToPad & 0xFF);
            byte[] padding = new byte[amountToPad];
            for (int i = 0; i < amountToPad; i++) padding[i] = pad;
            return padding;
        }
    }

    private boolean verifySignature(String token, String timestamp, String nonce, String echostr, String msgSignature) {
        try {
            String[] array = new String[]{token, timestamp, nonce, echostr};
            Arrays.sort(array);
            StringBuilder sb = new StringBuilder();
            for (String str : array) sb.append(str);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes());
            StringBuilder hexStr = new StringBuilder();
            for (byte b : digest) {
                String shaHex = Integer.toHexString(b & 0xFF);
                if (shaHex.length() < 2) hexStr.append(0);
                hexStr.append(shaHex);
            }
            return hexStr.toString().equals(msgSignature);
        } catch (Exception e) { return false; }
    }

    private String decrypt(String echostr, String encodingAesKey) throws Exception {
        byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        byte[] msgBytes = Base64.getDecoder().decode(echostr);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        byte[] decrypted = cipher.doFinal(msgBytes);
        int networkLength = ((decrypted[16] & 0xFF) << 24) | ((decrypted[17] & 0xFF) << 16) | ((decrypted[18] & 0xFF) << 8) | (decrypted[19] & 0xFF);
        return new String(Arrays.copyOfRange(decrypted, 20, 20 + networkLength), StandardCharsets.UTF_8);
    }
}
