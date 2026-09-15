# 改进与运行说明

改进针对实际运行的后端、管理端、小程序编译产物及 Nginx 配置；没有部署或修改外部数据库。代码发布范围见 `PROJECT.md`，具体推送状态以 GitHub 远端为准。

## 已完成

- 地址与用户订单接口增加归属校验；管理员令牌还会检查账户是否启用。请求结束清理线程中的用户身份。
- 下单由服务端按当前在售商品重新计价，不信任客户端金额；锁定购物车，在同一事务内保存订单、明细和清空购物车。修复数量 SQL、分类筛选及完成订单状态。
- 修复配送范围查询的响应解析、参数拼写，增加超时及错误处理。
- 微信支付回调校验 RSA 签名、AES-GCM 解密、时间戳、商户、应用和金额；通过订单号查找订单，处理重复通知及取消后的迟到支付。状态变更使用行锁，推送在事务提交后触发。退款使用原订单金额并检查接口响应。
- WebSocket 必须先发送管理端 JWT，未认证连接不接收订单消息；检查令牌过期、员工禁用及握手同源性。前端按页面协议选择 ws/wss。
- 运行配置中的密钥改为环境变量，要求管理端和用户端 JWT 密钥不同且各至少 32 字节；隐藏员工密码字段，去除登录凭据、OSS 密钥和支付报文日志。
- 补齐运营报表模板及导出响应；修复套餐搜索后的菜品选择、重复请求取消异常。
- 小程序服务器地址集中到 `mp-weixin/config.js`，开启域名检查；移除依赖锁文件中失效的淘宝镜像地址。Lombok 升级以兼容本机 JDK 21。
- 新增回归测试，并移除原来忽略整个后端测试目录的 Git 规则。

## 本机验证

验证环境：JDK 21、Maven 3.9.16、Node.js 22.23.2。

在仓库根目录运行：

```powershell
Set-Location sky-take-out
mvn -q test
Set-Location ../project-rjwm-admin-vue-ts
npm ci --ignore-scripts --legacy-peer-deps --no-audit --no-fund --registry=https://registry.npmjs.org
npm run test:unit -- --runInBand
npm run build:compat
Set-Location ..
node mp-weixin/config.test.js
```

后端测试使用模拟外部接口与 H2 的 MySQL 兼容模式，不需要连接真实数据库。覆盖越权、服务端计价、事务回滚、支付验签/幂等、状态流转、WebSocket、SQL 和报表导出；不等同于真实 MySQL 并发及微信端到端联调。

前端是旧版 Vue CLI 3 / webpack 4：`build:compat` 为现代 Node 提供所需的 OpenSSL 兼容开关，仅用于本地构建。安装时跳过旧 fibers/Cypress 安装脚本，生产构建会回退到 Sass；可能打印 fibers 缺失、旧 Jest 版本和包体积告警。没有安装或运行 Cypress 浏览器端到端测试。后续应单独规划前端工具链升级，不要通过关闭 TLS 校验下载依赖。

## 启动前配置

在 IDE 的运行配置或启动进程环境中设置变量，不要把真实凭据写回仓库。所有变量及默认值见 `sky-take-out/sky-server/src/main/resources/application-dev.yml` 和 `application.yml`。

| 用途 | 环境变量 |
| --- | --- |
| 身份认证（必须设置） | `SKY_JWT_ADMIN_SECRET`、`SKY_JWT_USER_SECRET`：使用两个独立的随机密钥，各至少 32 字节 |
| MySQL | `SKY_DB_HOST`、`SKY_DB_PORT`、`SKY_DB_NAME`、`SKY_DB_USER`、`SKY_DB_PASSWORD` |
| Redis | `SKY_REDIS_HOST`、`SKY_REDIS_PORT`、`SKY_REDIS_PASSWORD`、`SKY_REDIS_DATABASE` |
| OSS 上传 | `SKY_OSS_ENDPOINT`、`SKY_OSS_ACCESS_KEY_ID`、`SKY_OSS_ACCESS_KEY_SECRET`、`SKY_OSS_BUCKET` |
| 微信登录 | `SKY_WECHAT_APP_ID`、`SKY_WECHAT_SECRET` |
| 微信支付 | `SKY_WECHAT_MCH_ID`、`SKY_WECHAT_MCH_SERIAL_NO`、`SKY_WECHAT_PRIVATE_KEY_PATH`、`SKY_WECHAT_API_V3_KEY`、`SKY_WECHAT_PLATFORM_CERT_PATH`、`SKY_WECHAT_NOTIFY_URL` |
| 配送 | `SKY_SHOP_ADDRESS`、`SKY_BAIDU_AK` |
| 费用 | `SKY_PACKAGING_FEE_PER_ITEM` 默认每件 1 元；`SKY_DELIVERY_FEE` 默认每单 6 元 |

数据库需先初始化原项目表结构，再运行 `com.sky.SkyApplication`。JWT 缺失/不符合要求会阻止启动；未配置地图密钥将拒绝下单；OSS、微信功能也需要有效配置。测试通过并不意味着外部服务已配置。

### 管理端与反向代理

管理端生产请求使用同源 `/api`、WebSocket 使用 `/ws/`。构建后的 `dist` 需由部署人员发布到 Nginx 静态目录；本轮未覆盖 `nginx-1.20.2/html/sky` 的旧静态产物，也没有启动或重载 Nginx。

Nginx 已补充 `/notify/` 代理，微信支付成功通知地址应为 `https://你的域名/notify/paySuccess`。上线前配置真实域名和 TLS；当前示例仍只监听 HTTP 80。WebSocket 代理保留 Host 和协议头以支持同源校验。启用了后端转发头处理，因此 8080 端口应仅允许可信反向代理访问，代理需覆盖而不是信任客户端提交的转发头。

微信平台证书必须由可信渠道获得并按轮换计划更新；回调每次重新读取本地证书文件，并检查有效期。当前使用单个证书文件，轮换重叠期的多证书管理仍需完善。商户私钥与 API v3 密钥仅在服务端保存。

### 小程序

把 `mp-weixin/config.js` 中的 `apiBaseUrl` 改为实际可访问的 HTTPS 根地址，例如 `https://你的域名`，不要追加 `/user`；在微信后台登记合法域名，并替换 `project.config.json` 的 AppID 为自己的应用。真机不能使用 localhost。

本仓库只有编译产物，暂无原始 UniApp 工程，因此修改了编译产物中的配置入口。以后重新编译时，应在原始工程同步该配置机制，避免覆盖此修复；还需在微信开发者工具与真机验证登录、下单、支付。改动费用配置时也需同步小程序费用展示；默认每件 1 元包装费和每单 6 元配送费与现有客户端一致，实际收款始终以服务端结果为准。

## 仍需处理的上线事项

1. **轮换曾暴露的全部凭据。** 移除运行配置不等于吊销凭据；原仓库历史和本地课程资料副本仍可能含旧值。新发布提交不包含这些历史和教学资料，但没有改写原仓库历史或删除本地资料。请在对应平台撤销旧密钥，并检查现有日志、权限和访问记录。
2. **退款最终状态对账。** 现有数据模型的 `REFUND` 表示退款申请已受理，不保证资金已到账。需补充退款处理中/成功/失败状态、退款通知或主动查询及人工补偿机制；没有实现退款通知接口时，不要填写 `SKY_WECHAT_REFUND_NOTIFY_URL`。
3. **真实环境联调。** 核验 MySQL 事务/索引和并发行为、Redis、OSS、百度地图、微信支付/退款及 WebSocket 代理，测试支付与取消竞态；尚未执行真实支付或退款。
4. **遗留安全与依赖升级。** 旧 MD5 密码存储和默认初始密码仍需迁移到现代密码哈希及首次登录改密；框架、前端依赖、鉴权权限分级与接口限流需要后续专项改造。本轮不是完整生产安全认证。
