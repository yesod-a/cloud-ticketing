<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createOrder, createQuantityOrder, getActivity, getSeats } from '../api'
import { myOrders } from '../adminApi'
import type { Activity, Seat, Session } from '../types'
const props=defineProps<{activity:Activity}>(); const emit=defineEmits<{back:[];pay:[orderId:string]}>()
const sessions=ref<Session[]>([]), selectedSession=ref(''), seats=ref<Seat[]>([]), selected=ref<string[]>([]), quantity=ref(1), loading=ref(true), error=ref(''), order=ref<Record<string,string>|null>(null), detailActivity=ref<Activity>(props.activity), remainingPurchaseLimit=ref<number|null>(null)
const currentSession=computed(()=>sessions.value.find(s=>s.id===selectedSession.value))
const isGeneralAdmission=computed(()=>currentSession.value?.layoutMode==='GENERAL_ADMISSION')
const maxQuantity=computed(()=>{const session=currentSession.value;if(!session||!isGeneralAdmission.value)return 0;const capacity=session.remainingCapacity??session.capacity??0;const limit=remainingPurchaseLimit.value ?? (session.purchaseLimit&&session.purchaseLimit>0?session.purchaseLimit:Number.MAX_SAFE_INTEGER);return Math.max(0,Math.min(capacity,limit))})
const isAvailable=(seat:Seat)=>['AVAILABLE','available'].includes(seat.status)
const areaGroups=computed(()=>{const areas=new Map<string,Seat[]>();for(const s of seats.value){const key=s.areaLabel||'全场';const list=areas.get(key)||[];list.push(s);areas.set(key,list)}return Array.from(areas.entries()).map(([area,list])=>{const rows=new Map<number,Seat[]>();for(const s of list){const y=s.y??0;const r=rows.get(y)||[];r.push(s);rows.set(y,r)}return{area,rows:Array.from(rows.entries()).map(([y,rowSeats])=>rowSeats.slice().sort((a,b)=>(a.x??0)-(b.x??0)))}})})
async function loadPurchaseAllowance(){const session=currentSession.value;if(!session||!isGeneralAdmission.value){remainingPurchaseLimit.value=null;return}remainingPurchaseLimit.value=session.remainingPurchaseLimit??null;if(!session.purchaseLimit||session.purchaseLimit<=0)return;try{const result=await myOrders({page:0,size:100});const used=result.items.filter(row=>row.sessionId===session.id&&['PENDING','PAID'].includes(row.status)).reduce((sum,row)=>sum+(row.quantity??0),0);remainingPurchaseLimit.value=Math.max(0,session.purchaseLimit-used)}catch{remainingPurchaseLimit.value=session.remainingPurchaseLimit??session.purchaseLimit}}
async function loadSeats(){if(!selectedSession.value)return;loading.value=true;error.value='';selected.value=[];quantity.value=1;if(isGeneralAdmission.value){seats.value=[];await loadPurchaseAllowance();loading.value=false;return}remainingPurchaseLimit.value=null;try{seats.value=await getSeats(selectedSession.value)}catch{error.value='座位数据加载失败，请稍后重试。';seats.value=[]}finally{loading.value=false}}
function toggle(seat:Seat){if(!isAvailable(seat))return;selected.value=selected.value.includes(seat.id)?selected.value.filter(id=>id!==seat.id):selected.value.length<6?[...selected.value,seat.id]:selected.value}
async function submit(){try{order.value=isGeneralAdmission.value?await createQuantityOrder(selectedSession.value,quantity.value,crypto.randomUUID()):await createOrder(selectedSession.value,selected.value,crypto.randomUUID())}catch{error.value='订单创建失败，请稍后重试。'}}
onMounted(async()=>{try{const d=await getActivity(props.activity.id);detailActivity.value={...props.activity,...d.activity};sessions.value=d.sessions;selectedSession.value=sessions.value[0]?.id||'';await loadSeats()}catch{error.value='场次数据加载失败，请稍后重试。'}finally{loading.value=false}})
</script>
<template>
  <section class="booking-panel">
    <button class="secondary-btn" @click="emit('back')">← 返回活动</button>
    <p class="eyebrow">{{ activity.organizer }}</p>
    <h1>{{ activity.title }}</h1>
    <p v-if="detailActivity.description" class="activity-description booking-description">{{ detailActivity.description }}</p>
    <div v-if="detailActivity.images?.length" class="activity-gallery"><img v-for="image in detailActivity.images" :key="image.id" :src="image.url" :alt="detailActivity.title" loading="lazy"></div>
    <label class="muted-label">选择场次
      <select v-model="selectedSession" @change="loadSeats"><option v-for="s in sessions" :key="s.id" :value="s.id">{{ new Date(s.startsAt).toLocaleString('zh-CN') }} · {{ s.venue }}</option></select>
    </label>
    <p v-if="error" class="alert">{{ error }}</p>
    <p v-if="loading">正在加载座位...</p>
    <div v-else-if="!isGeneralAdmission" class="seat-stage">
      <div class="stage-label">舞台 STAGE</div>
      <div v-for="group in areaGroups" :key="group.area" class="seat-area">
        <div class="area-label">{{ group.area }}</div>
        <div v-for="(row, index) in group.rows" :key="index" class="seat-row">
          <span v-for="seat in row" :key="seat.id" class="seat" :class="{selected:selected.includes(seat.id),locked:!isAvailable(seat),vip:seat.type==='VIP'}" :title="seat.displayName || `${seat.row}${seat.number}`" :disabled="!isAvailable(seat)" @click="toggle(seat)">{{ seat.displayName || `${seat.row}${seat.number}` }}</span>
        </div>
      </div>
    </div>
    <div v-if="isGeneralAdmission" class="quantity-card"><p>剩余 {{ currentSession?.remainingCapacity ?? currentSession?.capacity ?? 0 }} 张<span v-if="currentSession?.purchaseLimit"> · 每人限购 {{ currentSession.purchaseLimit }} 张 · 剩余可购 {{ remainingPurchaseLimit ?? currentSession.purchaseLimit }} 张</span></p><label>购票数量<input data-testid="quantity-input" v-model.number="quantity" type="number" min="1" :max="maxQuantity"></label></div>
    <div v-else class="seat-summary"><span class="seat-legend"><i class="dot available"></i>可选</span><span class="seat-legend"><i class="dot selected"></i>已选</span><span class="seat-legend"><i class="dot locked"></i>不可选</span></div>
    <button v-if="isGeneralAdmission" class="primary-btn" data-testid="quantity-submit" :disabled="quantity<1||quantity>maxQuantity||!!order" @click="submit">{{ order?'订单已创建':'确认购买（'+quantity+'）' }}</button>
    <button v-else class="primary-btn" :disabled="!selected.length||!!order" @click="submit">{{ order?'订单已创建':'确认选座（'+selected.length+'）' }}</button>
    <div v-if="order" class="order-result">
      <p>订单号：{{ order.id }}　状态：{{ order.status }}</p>
      <button class="primary-btn" @click="emit('pay', order.id)">去支付</button>
    </div>
  </section>
</template>
<style scoped>
.seat-stage{margin:22px 0;padding:20px;background:#0b111d;border:1px solid #24334d;border-radius:14px}.stage-label{text-align:center;letter-spacing:.35em;color:#8aa0b8;padding:10px 0 18px;border-bottom:2px solid #2c3b55;margin-bottom:18px}.seat-area{margin:0 0 20px}.area-label{color:#43d5b3;font-size:12px;font-weight:700;margin:8px 0}.seat-row{display:flex;justify-content:center;gap:7px;margin:5px 0;flex-wrap:wrap}.seat{width:34px;height:34px;display:grid;place-items:center;border-radius:6px;background:#183f37;color:#43d5b3;font-size:10px;cursor:pointer;border:1px solid #2b7a67;transition:transform .12s}.seat:hover:not(.locked){transform:translateY(-2px)}.seat.selected{background:#f2ab5c;color:#1c1308;border-color:#f2ab5c}.seat.locked{background:#131c2c;color:#4a5870;border-color:#24314a;cursor:not-allowed}.seat.vip{box-shadow:0 0 0 1px #f2ab5c inset}.seat-summary{display:flex;gap:18px;justify-content:center;margin:14px 0;color:#8aa0b8;font-size:12px}.dot{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:6px}.dot.available{background:#183f37}.dot.selected{background:#f2ab5c}.dot.locked{background:#131c2c}
.booking-description{margin:10px 0 16px;color:#b9c5d6;white-space:pre-wrap}.activity-gallery{display:flex;gap:10px;overflow-x:auto;margin:0 0 18px}.activity-gallery img{width:150px;height:96px;object-fit:cover;border-radius:8px;border:1px solid #31405a}
</style>
