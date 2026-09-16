<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminVenuePage, adminVenueSeats, createAdminVenue, createAdminVenueSeat, deleteAdminVenueSeat, generateAdminVenueLayout, updateAdminVenue, type AdminVenue, type AdminVenueSeat } from '../../adminApi'

const props = defineProps<{ permissions: string[] }>()
const can = (permission: string) => props.permissions.includes(permission) || props.permissions.includes('system:config')
const venues = ref<AdminVenue[]>([]), page = ref(0), total = ref(0), keyword = ref(''), error = ref(''), loading = ref(false)
const seats = ref<AdminVenueSeat[]>([]), selectedVenueId = ref(''), selectedVenue = ref<AdminVenue|null>(null)
const showVenueModal = ref(false), editingVenue = ref<AdminVenue|null>(null), venueName = ref(''), venueAddress = ref('')
const layoutMode = ref<'GRID'|'ROWS'>('GRID'), layoutArea = ref(''), rowCount = ref(10), seatsPerRow = ref(20), rowLabelType = ref<'LETTER'|'NUMBER'>('LETTER'), startSeatNumber = ref(1), rowsList = ref<{rowLabel:string;seatCount:number}[]>([{rowLabel:'A',seatCount:20}])
const showSeatModal = ref(false), seatArea = ref(''), seatRow = ref('A'), seatNumber = ref(1), seatDisplay = ref(''), seatX = ref(0), seatY = ref(0), seatType = ref('REGULAR')
const groupedSeats = computed(() => { const map = new Map<string, AdminVenueSeat[]>(); for (const s of seats.value) { const key = `${s.areaLabel || '未分区'}｜${s.rowLabel}`; const list = map.get(key) || []; list.push(s); map.set(key, list) } return Array.from(map.entries()).map(([label, list]) => ({ label, list: list.slice().sort((a,b)=>a.x-b.x||a.seatNumber-b.seatNumber) })) })
async function loadVenues() { loading.value = true; error.value = ''; try { const result = await adminVenuePage({ keyword: keyword.value, page: page.value, size: 10 }); venues.value = result.items; total.value = result.total } catch { error.value = '场馆加载失败，请检查权限或服务状态。' } finally { loading.value = false } }
async function loadSeats(id = selectedVenueId.value) { if (!id) { seats.value = []; return } try { seats.value = (await adminVenueSeats(id)).items } catch { seats.value = []; error.value = '座位模板加载失败。' } }
function openCreateVenue() { editingVenue.value = null; venueName.value = ''; venueAddress.value = ''; showVenueModal.value = true }
function openEditVenue(row: AdminVenue) { editingVenue.value = row; venueName.value = row.name; venueAddress.value = row.address; showVenueModal.value = true }
async function saveVenue() { if (!venueName.value.trim()) return; try { if (editingVenue.value) await updateAdminVenue(editingVenue.value.id, venueName.value, venueAddress.value); else await createAdminVenue(venueName.value, venueAddress.value); showVenueModal.value = false; await loadVenues() } catch { error.value = '场馆保存失败。' } }
async function selectVenue(row: AdminVenue) { selectedVenueId.value = row.id; selectedVenue.value = row; await loadSeats(row.id) }
function addRow() { rowsList.value.push({ rowLabel: String.fromCharCode(65 + rowsList.value.length), seatCount: 20 }) }
function removeRow(index: number) { rowsList.value.splice(index, 1) }
async function generateLayout() { if (!selectedVenueId.value) return; try { const body: any = layoutMode.value === 'GRID' ? { mode: 'GRID', areaLabel: layoutArea.value, rowCount: rowCount.value, seatsPerRow: seatsPerRow.value, rowLabelType: rowLabelType.value, startSeatNumber: startSeatNumber.value } : { mode: 'ROWS', areaLabel: layoutArea.value, rows: rowsList.value }; seats.value = await generateAdminVenueLayout(selectedVenueId.value, body) } catch { error.value = '座位布局生成失败。' } }
async function addSeat() { if (!selectedVenueId.value || !seatRow.value.trim()) return; try { const created = await createAdminVenueSeat(selectedVenueId.value, { areaLabel: seatArea.value, rowLabel: seatRow.value, seatNumber: seatNumber.value, displayName: seatDisplay.value, x: seatX.value, y: seatY.value, seatType: seatType.value }); seats.value = [...seats.value, created]; showSeatModal.value = false } catch { error.value = '座位新增失败。' } }
async function removeSeat(seat: AdminVenueSeat) { try { await deleteAdminVenueSeat(seat.venueId, seat.id); seats.value = seats.value.filter(s => s.id !== seat.id) } catch { error.value = '座位删除失败。' } }
onMounted(loadVenues)
</script>

<template>
  <div class="venue-inventory">
    <div class="admin-heading"><div><p class="eyebrow">INVENTORY</p><h2>场馆与库存</h2></div><div class="admin-actions"><button v-if="can('venue:write') || can('activity:write')" class="primary-btn compact" @click="openCreateVenue">新建场馆</button><button class="secondary-btn" @click="loadVenues">刷新</button></div></div>
    <div class="admin-filters"><input v-model="keyword" placeholder="搜索场馆" @keyup.enter="page=0;loadVenues()"></div>
    <p v-if="error" class="alert">{{ error }}</p>
    <p v-if="loading">正在加载...</p>
    <table v-else class="admin-table"><thead><tr><th>场馆</th><th>地址</th><th>座位数</th><th>操作</th></tr></thead><tbody><tr v-for="row in venues" :key="row.id"><td>{{ row.name }}</td><td>{{ row.address || '-' }}</td><td>{{ row.capacity }}</td><td><button v-if="can('venue:write') || can('activity:write')" class="icon-btn" @click="openEditVenue(row)">编辑</button><button v-if="can('seat-layout:write') || can('venue:read')" class="icon-btn" @click="selectVenue(row)">座位布局</button></td></tr><tr v-if="!venues.length"><td colspan="4">暂无场馆</td></tr></tbody></table>
    <div class="pagination"><button class="secondary-btn" :disabled="page===0" @click="page--;loadVenues()">上一页</button><span>第 {{ page+1 }} 页 · 共 {{ total }} 条</span><button class="secondary-btn" :disabled="(page+1)*10>=total" @click="page++;loadVenues()">下一页</button></div>

    <section v-if="selectedVenueId" class="admin-subpanel">
      <div class="admin-heading"><div><h3>{{ selectedVenue?.name }} · 座位布局</h3><small>{{ seats.length }} 个座位</small></div><button class="secondary-btn" @click="selectedVenueId='';selectedVenue=null;seats=[]">关闭</button></div>
      <form v-if="can('seat-layout:write')" class="admin-form layout-form" @submit.prevent="generateLayout">
        <label>生成方式<select v-model="layoutMode"><option value="GRID">统一行列</option><option value="ROWS">每行独立列数</option></select></label>
        <label>区域<input v-model="layoutArea" placeholder="如 VIP 区 / 看台"></label>
        <template v-if="layoutMode === 'GRID'">
          <label>行数<input v-model.number="rowCount" type="number" min="1"></label>
          <label>每行列数<input v-model.number="seatsPerRow" type="number" min="1"></label>
          <label>行号<select v-model="rowLabelType"><option value="LETTER">字母 A/B/C</option><option value="NUMBER">数字 1/2/3</option></select></label>
          <label>起始座号<input v-model.number="startSeatNumber" type="number" min="1"></label>
        </template>
        <template v-else>
          <div class="rows-editor"><div class="admin-heading"><h4>逐行配置</h4><button type="button" class="secondary-btn" @click="addRow">添加一行</button></div><div v-for="(row, index) in rowsList" :key="index" class="row-input"><input v-model="row.rowLabel" placeholder="排号"><input v-model.number="row.seatCount" type="number" min="1" placeholder="列数"><button type="button" class="icon-btn" @click="removeRow(index)">删除</button></div></div>
        </template>
        <button class="primary-btn">生成并替换座位</button>
      </form>
      <div class="admin-actions"><button v-if="can('seat-layout:write')" class="secondary-btn" @click="showSeatModal=true">新增单座</button></div>
      <div class="seat-preview"><div v-for="group in groupedSeats" :key="group.label" class="seat-preview-row"><span class="row-label">{{ group.label }}</span><span v-for="seat in group.list" :key="seat.id" class="seat-dot" :class="{disabled:!seat.enabled}">{{ seat.seatNumber }}</span></div><p v-if="!seats.length" class="muted">暂无座位，请先生成或新增。</p></div>
    </section>

    <div v-if="showVenueModal" class="modal-backdrop" @click.self="showVenueModal=false"><form class="modal-card" @submit.prevent="saveVenue"><div class="admin-heading"><h3>{{ editingVenue ? '编辑场馆' : '新建场馆' }}</h3><button type="button" class="icon-btn" @click="showVenueModal=false">×</button></div><input v-model="venueName" placeholder="场馆名称" required><input v-model="venueAddress" placeholder="地址"><div class="modal-actions"><button type="button" class="secondary-btn" @click="showVenueModal=false">取消</button><button class="primary-btn">保存</button></div></form></div>

    <div v-if="showSeatModal" class="modal-backdrop" @click.self="showSeatModal=false"><form class="modal-card" @submit.prevent="addSeat"><div class="admin-heading"><h3>新增单座</h3><button type="button" class="icon-btn" @click="showSeatModal=false">×</button></div><input v-model="seatArea" placeholder="区域"><input v-model="seatRow" placeholder="排号" required><input v-model.number="seatNumber" type="number" min="1" placeholder="座号" required><input v-model="seatDisplay" placeholder="显示名，如 VIP-A-8"><input v-model.number="seatX" type="number" placeholder="横向坐标"><input v-model.number="seatY" type="number" placeholder="纵向坐标"><input v-model="seatType" placeholder="座位类型"><div class="modal-actions"><button type="button" class="secondary-btn" @click="showSeatModal=false">取消</button><button class="primary-btn">保存</button></div></form></div>
  </div>
</template>

<style scoped>
.admin-subpanel{margin-top:24px;padding:18px;background:#0f1724;border:1px solid #2c3b55;border-radius:10px}.layout-form{display:grid;grid-template-columns:repeat(3,minmax(120px,1fr));gap:12px;margin:16px 0}.layout-form label{display:grid;gap:6px;color:#a8b4c4;font-size:12px}.layout-form input,.layout-form select{background:#151f31;border:1px solid #31405a;color:#e8edf5;border-radius:6px;padding:9px;font:inherit}.rows-editor{grid-column:1/-1;display:grid;gap:8px}.row-input{display:grid;grid-template-columns:1fr 1fr auto;gap:8px}.seat-preview{display:flex;flex-direction:column;gap:8px;margin-top:18px;padding:16px;background:#0b111d;border-radius:10px;overflow:auto}.seat-preview-row{display:flex;align-items:center;gap:5px;flex-wrap:wrap}.row-label{min-width:110px;color:#67758b;font-size:11px}.seat-dot{width:24px;height:24px;display:grid;place-items:center;border-radius:4px;background:#183f37;color:#43d5b3;font-size:10px}.seat-dot.disabled{opacity:.35}
</style>
