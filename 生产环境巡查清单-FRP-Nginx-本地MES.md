# 生产环境巡查清单（本地 MES + FRP + 阿里云 Nginx）

> 目标：固定域名 `https://api.maxtritape.com` 可持续可用，避免 502。

## 一、架构基线

- 小程序/前端 → `api.maxtritape.com`（阿里云 Nginx）
- 阿里云 Nginx → `127.0.0.1:18090`（FRP 映射端口）
- FRP 映射 → 本地 Windows `127.0.0.1:8090`（MES）

---

## 二、每日巡查（1 分钟）

### 1) 外网健康检查（先看结果）
- 访问：`https://api.maxtritape.com/actuator/health`
- 预期：`200` 且 body 包含 `{"status":"UP"}`

### 2) 登录链路检查
- `POST https://api.maxtritape.com/user/login`
- 预期：
  - `200 + 业务 code=200/20000`（登录成功）
  - 或 `200 + 业务 code=401`（账号密码错误）
- 不应出现：`502/504/超时`

---

## 三、分层排查（从外到内）

### A. 云端 Nginx（阿里云 ECS）
- `sudo nginx -t` 必须 successful
- `sudo systemctl status nginx` 必须 active (running)
- `sudo grep -n "include /etc/nginx/conf.d/*.conf" /etc/nginx/nginx.conf`
  - 预期：只出现 **1 次**（避免重复 include 导致 server_name 冲突）
- `api.maxtritape.com.conf` 核心应为：
  - `listen 443 ssl`
  - `proxy_pass http://127.0.0.1:18090;`

### B. 云端 FRP 服务端 `frps`
- `sudo systemctl status frps` 必须 active (running)
- `sudo ss -lntp | grep 7000` 必须监听

### C. 云端回源端口
- `curl -i http://127.0.0.1:18090/actuator/health`
- 预期：`200`

### D. 本地 Windows FRP 客户端 `frpc`
- 进程存在：`Get-Process frpc`
- 配置文件：`C:\tools\frp\frpc.ini`
  - `server_addr=120.77.211.88`
  - `server_port=7000`
  - `remote_port=18090`
  - `local_port=8090`

### E. 本地 MES
- `Get-NetTCPConnection -LocalPort 8090 -State Listen`
- 预期：8090 被 `java.exe` 监听

---

## 四、自动启动（必须项）

### 云端（ECS）
- `nginx`：`systemctl enable nginx`
- `frps`：`systemctl enable frps`

### 本地（Windows）
- `frpc`：任务计划程序（开机/登录触发）
- MES：建议用 NSSM / 任务计划 / 自建服务，确保开机自动拉起

---

## 五、常见故障与快速结论

### 现象 1：固定域名返回 502
优先检查：
1. Nginx 配置冲突（重复 include 或重复 server_name）
2. `127.0.0.1:18090` 不通（frpc 未连上）
3. 本地 MES 8090 未启动

### 现象 2：`/actuator/health` 正常，但登录失败
- 这通常是业务账号问题（401），不是网络问题

### 现象 3：偶发恢复后又失败
- 常见原因：本地重启后 `frpc` 或 MES 未自动拉起

---

## 六、应急恢复顺序（推荐）

1. 启本地 MES（确认 8090）
2. 启本地 frpc（确认进程）
3. 云端查 `curl http://127.0.0.1:18090/actuator/health`
4. `sudo nginx -t && sudo systemctl reload nginx`
5. 复测 `https://api.maxtritape.com/actuator/health`

---

## 七、运维建议

- 不再使用 cpolar 动态域名回源
- 固定使用 FRP：`api.maxtritape.com -> 127.0.0.1:18090 -> 本地 8090`
- 账号密码与网络问题分离处理，避免误判
