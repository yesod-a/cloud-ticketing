# 管理员审计闭环任务报告

## 本轮补齐内容

### Auth 服务

- 角色授予要求 `role:manage`（或 `system:config`）权限。
- 只有 `auth_user_role` 实际插入成功时才递增用户 `scope_version` 并写入 `USER_ROLE_GRANTED` 审计记录。
- 重复角色授予不会重复递增版本或写审计；越权请求返回 HTTP 403。

### Activity 服务

- 活动创建、场次创建、发布、下线、布局冻结、座位更新均写入 `activity_audit_log`。
- 发布、下线、冻结和座位更新审计包含非空 `before_json`/`after_json` 快照。
- 新增场馆创建接口并记录 `VENUE_CREATED` 审计。

## 验证证据

```text
mvn -pl services/auth-service -am -Dtest=AuthAdminAuditTest "-Dsurefire.failIfNoSpecifiedTests=false" test
# 3/3 passed

mvn -pl services/activity-service -am -Dtest=ActivityAuditControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
# 2/2 passed

mvn --% -q -Dsurefire.failIfNoSpecifiedTests=false test
# exit code 0

npm test -- --run
# 10 files / 16 tests passed

npm run build
# Vite production build passed

docker compose config -q
git diff --check
# both passed; only LF/CRLF normalization warnings were emitted by Git
```

## 已知边界

- 支付、出票和对账仅有版本化事件/规则契约文档，当前未声称运行时实现。
- `processed_event` 存储已提供重复事件去重契约，但尚未实现具体业务消费者或 DLQ/retry 消费链路。
- Gateway 与 Nacos 的注册和负载均衡已在 Compose 环境验证；跨环境故障转移和生产级密钥轮换仍需部署环境验证。
- 审计接口返回分页数据；Auth 审计表已有不可变触发器，Activity 审计表在生产部署仍应补充数据库权限/不可变策略。
