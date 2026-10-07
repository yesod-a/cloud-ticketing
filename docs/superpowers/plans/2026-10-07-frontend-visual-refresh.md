# Cloud Ticketing Frontend Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改变业务接口、认证流程和测试选择器的前提下，将 Vue 前端调整为深色现场票务平台风格，并改善用户端主购票链路、后台和移动端体验。

**Architecture:** 保留现有 `App.vue` 的页面状态切换和 Vue 单文件组件结构。先在现有全局 CSS 中建立设计令牌、公共状态和响应式基础，再按页面范围调整模板与局部样式；不新增 UI 框架，不修改 API 数据契约。

**Tech Stack:** Vue 3, TypeScript, Vite, CSS, lucide-vue-next, Vitest, Vue Test Utils

**Spec:** `docs/superpowers/specs/2026-10-07-frontend-visual-refresh-design.md`

## Global Constraints

- 保留现有 API 调用、认证逻辑、订单状态和 `data-testid`。
- 不新增后端接口，不伪造统计数据，不把模拟支付改成真实支付。
- 保持 Vue 3 + TypeScript + Vite + CSS + `lucide-vue-next`。
- 375px 宽度下不得产生不可用的横向溢出。

### Task 1: 建立视觉令牌与公共状态样式

**Files:**
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/navigation-permissions.test.ts` (regression only)

- [x] **Step 1: 将颜色、边框、圆角、阴影和间距集中为 CSS variables**
- [x] **Step 2: 调整基础 body、button、input、focus-visible 和公共面板样式**
- [x] **Step 3: 增加统一状态标签、空状态、加载状态和图片占位样式**
- [x] **Step 4: 运行 `cd web; npm run test -- --run`，确认行为测试不回归**
- [x] **Step 5: 运行 `cd web; npm run build`，确认样式改动可编译**

### Task 2: 改造登录页与活动列表

**Files:**
- Modify: `web/src/views/LoginView.vue`
- Modify: `web/src/App.vue`
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/activity-list.test.ts`, `web/src/__tests__/auth-session.test.ts`

- [x] **Step 1: 保持登录、注册字段和测试选择器不变，增强输入状态、加载态和错误反馈**
- [x] **Step 2: 将活动卡片改成封面主视觉布局，并为无封面活动提供占位样式**
- [x] **Step 3: 保持关键词、主办方筛选和分页逻辑，补充活动元信息与状态层级**
- [x] **Step 4: 验证活动列表和认证测试**
- [x] **Step 5: 构建前端并检查 375px 下卡片无溢出**

### Task 3: 改造选座、通票、订单与支付视觉层级

**Files:**
- Modify: `web/src/views/BookingView.vue`
- Modify: `web/src/views/OrdersView.vue`
- Modify: `web/src/views/PaymentView.vue`
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/booking-api.test.ts`, `web/src/__tests__/general-admission.test.ts`, `web/src/__tests__/payment-flow.test.ts`

- [x] **Step 1: 保留选座业务逻辑，强化活动头部、场次选择、座位图例和订单摘要**
- [x] **Step 2: 让通票模式明确展示容量、限购和待出票信息，不显示误导性的实体座位提示**
- [x] **Step 3: 保留订单表格数据和操作，增加统一中文状态样式与移动端卡片布局**
- [x] **Step 4: 保留模拟支付和二维码逻辑，增加支付步骤层级、金额和倒计时强调**
- [x] **Step 5: 验证购票、通票和支付测试，并运行生产构建**

### Task 4: 调整个人中心、管理后台与响应式细节

**Files:**
- Modify: `web/src/views/ProfileView.vue`
- Modify: `web/src/views/admin/AdminLayout.vue`
- Modify: `web/src/views/admin/VenueInventoryView.vue`
- Modify: `web/src/styles.css`
- Test: `web/src/__tests__/profile-view.test.ts`, `web/src/__tests__/admin-console.test.ts`, `web/src/__tests__/admin-business-api.test.ts`

- [x] **Step 1: 将个人中心整理为账户概览、资料和安全操作的工作台布局**
- [x] **Step 2: 保留后台权限判断和 CRUD 行为，增强导航、表格、弹窗和危险操作状态**
- [x] **Step 3: 优化场馆/座位布局预览在桌面和移动端的可读性**
- [x] **Step 4: 运行全量前端测试和构建**
- [x] **Step 5: 用 `git diff --check` 检查空白错误并汇总改动**
