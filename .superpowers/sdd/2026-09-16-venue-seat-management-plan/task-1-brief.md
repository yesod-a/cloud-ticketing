### Task 1: 数据库座位模板与服务层

**Files:**
- Create: `services/activity-service/src/main/resources/db/migration/V6__venue_seat_template.sql`
- Modify: `services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCatalog.java`
- Test: `services/activity-service/src/test/java/com/cloudticket/activity/VenueSeatTemplateTest.java`

**Interfaces:**
- `ActivityCatalog.createVenue(String activityId,String name,String address)` 保持兼容并初始化空模板。
- 新增 `List<VenueSeat> venueSeats(String venueId)`、`VenueSeat createVenueSeat(String venueId,String rowLabel,int seatNumber,String position)`、`VenueSeat updateVenueSeat(String id,...)`、`void deleteVenueSeat(String id)`。
- `createSession(...)` 校验 venue 属于 activity、时间顺序正确，并把该 venue 的模板座位复制到新 session。

- [ ] **Step 1: Write the failing test**
  - 使用 Mockito/JdbcTemplate mock 验证座位模板新增参数被持久化；验证开始时间不早于结束时间抛出 `IllegalArgumentException`；验证创建场次会执行模板复制 SQL。
- [ ] **Step 2: Run test to verify it fails**
  - Run `mvn -pl services/activity-service -Dtest=VenueSeatTemplateTest test`; Expected: FAIL because methods/table do not exist。
- [ ] **Step 3: Write minimal implementation**
  - 建立 `venue_seat` 表（`id`、`venue_id`、`row_label`、`seat_number`、`position_x`、`position_y`、`status`，唯一键 `venue_id,row_label,seat_number`，外键到 `venue`）。
  - 在 `ActivityCatalog` 增加 record `VenueSeat` 与 CRUD 方法；创建场次时执行 `INSERT ... SELECT` 复制模板，模板为空时允许场次创建但返回空座位。
- [ ] **Step 4: Run test to verify it passes**
  - 重跑同一 Maven 测试，Expected: PASS。
- [ ] **Step 5: Commit**
  - `git add services/activity-service/src/main/resources/db/migration/V6__venue_seat_template.sql services/activity-service/src/main/java/com/cloudticket/activity/service/ActivityCatalog.java services/activity-service/src/test/java/com/cloudticket/activity/VenueSeatTemplateTest.java && git commit -m "feat: add venue seat templates"`


