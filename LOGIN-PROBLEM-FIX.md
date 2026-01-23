# 鐧诲綍闂蹇€熻瘖鏂笌瑙ｅ喅

## 馃搮 鏃ユ湡
2026-01-05

## 馃攳 闂璇婃柇

### 闂锛氭棤娉曠櫥褰曠郴缁?

---

## 鉁?宸叉墽琛岀殑鎿嶄綔

### 1. 妫€鏌ユ湇鍔＄姸鎬?
- 鉂?鍚庣鏈嶅姟 (8090) - 鏈繍琛?
- 鉂?鍓嶇鏈嶅姟 (8080) - 鏈繍琛?

**鍘熷洜**: 鏈嶅姟鍋滄鎴栨湭鍚姩

### 2. 鍚姩鏈嶅姟
- 鉁?鍚庣鏈嶅姟鍚姩鍛戒护宸叉墽琛岋紙鏂扮獥鍙ｏ級
- 鉁?鍓嶇鏈嶅姟鍚姩鍛戒护宸叉墽琛岋紙鏂扮獥鍙ｏ級

---

## 馃殌 鐜板湪璇锋墽琛?

### 姝ラ1: 妫€鏌ユ柊绐楀彛鏄惁鎵撳紑
鎮ㄥ簲璇ョ湅鍒?涓柊鐨凱owerShell绐楀彛锛?
- **绐楀彛1**: 鍚庣鏈嶅姟锛堟樉绀篗aven鍜孲pring Boot鏃ュ織锛?
- **绐楀彛2**: 鍓嶇鏈嶅姟锛堟樉绀簄pm鍜寃ebpack鏃ュ織锛?

### 姝ラ2: 绛夊緟鏈嶅姟鍚姩
- **鍚庣**: 绾?0绉掞紝鐪嬪埌 `Started MesApplication` 鍗虫垚鍔?
- **鍓嶇**: 绾?0绉掞紝鐪嬪埌 `App running at: http://localhost:8080/` 鍗虫垚鍔?

### 姝ラ3: 璁块棶鐧诲綍椤甸潰
```
http://localhost:8080
```

### 姝ラ4: 鐧诲綍
- **鐢ㄦ埛鍚?*: admin
- **瀵嗙爜**: [鎮ㄧ殑绠＄悊鍛樺瘑鐮乚

---

## 鈿狅笍 濡傛灉杩樻槸鏃犳硶鐧诲綍

### 鎯呭喌A: 鐪嬩笉鍒版柊绐楀彛
**鍘熷洜**: PowerShell绐楀彛琚樆姝㈡垨鍦ㄥ悗鍙?

**瑙ｅ喅**:
```powershell
# 鎵嬪姩鍚姩鍚庣
cd E:\java\MES
mvn spring-boot:run

# 鎵嬪姩鍚姩鍓嶇锛堟柊鍛戒护琛岀獥鍙ｏ級
cd E:\vue\ERP
npm run dev
```

### 鎯呭喌B: 鍚庣鍚姩澶辫触
**鐥囩姸**: 鍚庣绐楀彛鏄剧ず閿欒淇℃伅

**鍙兘鍘熷洜**:
1. 绔彛8090琚崰鐢?
2. 鏁版嵁搴撹繛鎺ュけ璐?
3. Redis杩炴帴澶辫触

**蹇€熸鏌?*:
```powershell
# 妫€鏌ョ鍙ｅ崰鐢?
netstat -ano | findstr ":8090"

# 濡傛灉鏈夎繘绋嬪崰鐢紝鍋滄瀹?
taskkill /PID [杩涚▼ID] /F

# 閲嶆柊鍚姩鍚庣
cd E:\java\MES
mvn spring-boot:run
```

### 鎯呭喌C: 鍓嶇鍚姩澶辫触
**鐥囩姸**: 鍓嶇绐楀彛鏄剧ず閿欒

**瑙ｅ喅**:
```powershell
# 閲嶆柊瀹夎渚濊禆
cd E:\vue\ERP
rm -rf node_modules
npm install

# 閲嶆柊鍚姩
npm run dev
```

### 鎯呭喌D: 鏈嶅姟鍚姩鎴愬姛浣嗙櫥褰曞け璐?
**鐥囩姸**: 鍑虹幇401閿欒鎴栫櫥褰曞悗绔嬪嵆閫€鍑?

**鍙兘鍘熷洜**:
1. Redis鏈嶅姟鏈惎鍔?
2. Token楠岃瘉澶辫触
3. 鐢ㄦ埛鍚嶆垨瀵嗙爜閿欒

**瑙ｅ喅姝ラ**:

#### 1锔忊儯 妫€鏌edis
```powershell
netstat -ano | findstr ":6379"
```
濡傛灉娌℃湁杈撳嚭锛岄渶瑕佸惎鍔≧edis锛?
```powershell
# 鍚姩Redis鏈嶅姟
redis-server
```

#### 2锔忊儯 妫€鏌ョ敤鎴峰悕瀵嗙爜
纭浣跨敤姝ｇ‘鐨勭鐞嗗憳璐﹀彿锛?
- 榛樿鐢ㄦ埛鍚? `admin`
- 濡傛灉蹇樿瀵嗙爜锛屽彲鑳介渶瑕侀噸缃?

#### 3锔忊儯 娓呴櫎娴忚鍣ㄧ紦瀛?
```
鎸?Ctrl+Shift+Delete
娓呴櫎缂撳瓨鍜孋ookie
```

---

## 馃敡 瀹屾暣鐨勬湇鍔″惎鍔ㄦ祦绋?

### 鎺ㄨ崘锛氫娇鐢ㄧ嫭绔嬬殑PowerShell绐楀彛

#### 绐楀彛1: 鍚姩鍚庣
```powershell
cd E:\java\MES
mvn spring-boot:run
```
**鎴愬姛鏍囧織**: 
```
Started MesApplication in X.XXX seconds
```

#### 绐楀彛2: 鍚姩鍓嶇
```powershell
cd E:\vue\ERP
npm run dev
```
**鎴愬姛鏍囧織**:
```
App running at:
- Local:   http://localhost:8080/
```

#### 绐楀彛3: 锛堝彲閫夛級鍚姩Redis
```powershell
redis-server
```

---

## 馃搳 鏈嶅姟鐘舵€侀獙璇?

### 蹇€熼獙璇佸懡浠?
```powershell
Write-Host "=== 鏈嶅姟鐘舵€?===" -ForegroundColor Cyan
Write-Host ""

# 鍚庣
$backend = netstat -ano | findstr ":8090"
if ($backend) {
    Write-Host "鉁?鍚庣杩愯涓? -ForegroundColor Green
} else {
    Write-Host "鉂?鍚庣鏈繍琛? -ForegroundColor Red
}

# 鍓嶇
$frontend = netstat -ano | findstr ":8080"
if ($frontend) {
    Write-Host "鉁?鍓嶇杩愯涓? -ForegroundColor Green
} else {
    Write-Host "鉂?鍓嶇鏈繍琛? -ForegroundColor Red
}

# Redis
$redis = netstat -ano | findstr ":6379"
if ($redis) {
    Write-Host "鉁?Redis杩愯涓? -ForegroundColor Green
} else {
    Write-Host "鈿狅笍  Redis鏈繍琛? -ForegroundColor Yellow
}
```

---

## 馃摑 甯歌鐧诲綍閿欒鐮?

### 401 Unauthorized
- **鍘熷洜**: 鏈櫥褰曟垨token杩囨湡
- **瑙ｅ喅**: 閲嶆柊鐧诲綍

### 403 Forbidden
- **鍘熷洜**: 娌℃湁璁块棶鏉冮檺
- **瑙ｅ喅**: 浣跨敤admin璐﹀彿鐧诲綍

### 500 Internal Server Error
- **鍘熷洜**: 鍚庣鏈嶅姟寮傚父
- **瑙ｅ喅**: 鏌ョ湅鍚庣鏃ュ織锛屾鏌ユ暟鎹簱/Redis杩炴帴

### Network Error
- **鍘熷洜**: 鏃犳硶杩炴帴鍚庣
- **瑙ｅ喅**: 纭鍚庣鏈嶅姟姝ｅ湪杩愯 (8090绔彛)

---

## 馃幆 鐧诲綍娴嬭瘯姝ラ

### 1. 纭鏈嶅姟閮藉湪杩愯
```powershell
netstat -ano | findstr ":8090"  # 鍚庣
netstat -ano | findstr ":8080"  # 鍓嶇
```

### 2. 璁块棶鐧诲綍椤甸潰
```
http://localhost:8080
```

### 3. 杈撳叆鍑嵁
- 鐢ㄦ埛鍚? `admin`
- 瀵嗙爜: [鎮ㄧ殑瀵嗙爜]

### 4. 瑙傚療琛屼负
- 鉁?**鎴愬姛**: 璺宠浆鍒伴椤?
- 鉂?**澶辫触**: 璁板綍閿欒淇℃伅

### 5. 濡傛灉澶辫触锛屾鏌ワ細
- 娴忚鍣ㄦ帶鍒跺彴 (F12) 鏄惁鏈夐敊璇?
- 鍚庣绐楀彛鏄惁鏈夊紓甯告棩蹇?
- Redis鏄惁姝ｅ湪杩愯

---

## 馃啒 浠嶇劧鏃犳硶瑙ｅ喅锛?

### 鏀堕泦璇婃柇淇℃伅

#### 1. 鏈嶅姟鐘舵€?
```powershell
netstat -ano | findstr ":8090 :8080 :6379"
```

#### 2. 鍚庣鏃ュ織
鏌ョ湅鍚庣绐楀彛鏈€鍚?0琛屾棩蹇?

#### 3. 鍓嶇閿欒
鎵撳紑娴忚鍣ㄥ紑鍙戣€呭伐鍏?(F12)锛屾煡鐪婥onsole鍜孨etwork鏍囩

#### 4. 灏濊瘯鐨勬楠?
璁板綍鎮ㄥ凡缁忓皾璇曡繃鐨勬墍鏈夋楠?

---

## 馃摎 鐩稿叧鏂囨。

- `FIX-401-ERROR.md` - 401閿欒淇鎸囧崡
- `quick-start-services.ps1` - 蹇€熷惎鍔ㄨ剼鏈?
- `check-redis-data.ps1` - Redis妫€鏌ヨ剼鏈?

---

## 鉁?瀹屾垚娓呭崟

- [ ] 鍚庣鏈嶅姟宸插惎鍔?(8090)
- [ ] 鍓嶇鏈嶅姟宸插惎鍔?(8080)
- [ ] Redis鏈嶅姟宸插惎鍔?(6379)
- [ ] 鍙互璁块棶鐧诲綍椤甸潰
- [ ] 鍙互鎴愬姛鐧诲綍
- [ ] 瀵艰埅鏍忔樉绀?棣栭〉"

---

**褰撳墠鐘舵€?*: 鏈嶅姟鍚姩鍛戒护宸叉墽琛? 
**涓嬩竴姝?*: 绛夊緟绾?0绉掞紝鐒跺悗璁块棶 http://localhost:8080  
**濡傛湁闂**: 鏌ョ湅鏂版墦寮€鐨勭獥鍙ｄ腑鐨勯敊璇俊鎭?

