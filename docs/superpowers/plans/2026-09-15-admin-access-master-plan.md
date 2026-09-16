# Master 管理端权限闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让 master 分支具备可登录的管理员账号、真实角色权限 JWT，以及按权限显示的管理端入口。

**Architecture:** auth-service 从数据库查询用户角色及权限，在签发 JWT 时写入 `roles` 和 `permissions`；gateway 只验证签名并转发可信身份头；Vue 前端从 JWT claims 计算管理入口和菜单可见性。管理员账号通过 Flyway 可重复初始化脚本创建，密码只保存 BCrypt 哈希。

**Tech Stack:** Spring Boot 3.5, Spring Data JDBC, Flyway, MySQL 8.4, Vue 3, Vite, Vitest。

**Spec:** `docs/superpowers/specs/2026-09-13-auth-rbac-multi-activity-design.md`

## Global Constraints

- 只修改当前 `master` 工作区，不切换或修改其它分支。
- 不保存明文密码；管理员初始密码通过环境变量注入并在启动时哈希。
- 普通用户不得获得管理权限；管理员权限来自数据库角色映射。
- 所有新行为先写失败测试，再实现并运行验证。

---

### Task 1: 角色权限查询与 JWT claims

**Files:**
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/repository/UserRepository.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/service/AuthService.java`
- Modify: `services/auth-service/src/main/java/com/cloudticket/auth/security/TokenService.java`
- Test: `services/auth-service/src/test/java/com/cloudticket/auth/AuthServiceTest.java`

- [x] 写测试：登录用户返回的 token payload 包含该用户角色和权限；普通用户权限为空。
- [x] 运行 `mvn --% -q -pl services/auth-service -am -Dtest=AuthServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 确认测试先失败。
- [x] 增加按用户 ID 查询角色/权限的 repository 查询，在 `AuthService.login` 将 claims 传给 `TokenService.issue`。
- [x] 扩展 `TokenService.issue` 和 JWT payload 序列化，写入 roles/permissions。
- [x] 运行同一测试确认通过。

### Task 2: 管理员账号初始化

**Files:**
- Create: `services/auth-service/src/main/resources/db/migration/V2__seed_admin_account.sql`
- Modify: `services/auth-service/src/main/resources/application.yml`
- Test: `services/auth-service/src/test/java/com/cloudticket/auth/AuthSchemaTest.java`

- [x] 写测试：管理员初始化迁移包含可配置账号占位符说明、SUPER_ADMIN 角色关联和幂等约束。
- [x] 运行 auth schema 测试确认先失败。
- [x] 增加安全的初始化方案：应用启动时读取 `AUTH_ADMIN_PHONE`、`AUTH_ADMIN_PASSWORD`，不存在则不创建；密码 BCrypt 哈希后插入 `auth_user` 并关联 `SUPER_ADMIN`。
- [x] 运行 schema 测试和 auth service 测试确认通过。

### Task 3: 前端权限识别与管理入口

**Files:**
- Create: `web/src/auth/tokenClaims.ts`
- Modify: `web/src/App.vue`
- Modify: `web/src/views/admin/AdminLayout.vue`
- Test: `web/src/__tests__/admin-route-guard.test.ts`

- [x] 写测试：SUPER_ADMIN 或任意管理权限可进入；普通 USER 不可进入；菜单按权限显示（由现有 token claims 与管理端测试覆盖）。
- [x] 运行 `npm test -- --run` 确认先失败。
- [x] 解析 JWT payload 的 roles/permissions，登录后写入权限状态，管理按钮和菜单使用权限判断。
- [x] 运行前端测试确认通过。

### Task 4: 端到端验证与记录

- [x] 运行全量 Maven 单元测试。
- [x] 运行全量 Vitest 和 Vite build。
- [x] 使用 Docker 重建 auth-service、gateway、web。
- [x] 验证管理员注册/登录返回权限 claims，普通用户不返回管理权限，数据库存在用户-角色关联。
- [x] 更新 `docs/evidence/auth-rbac-multi-activity-verification.md` 记录命令、结果和已知边界。
