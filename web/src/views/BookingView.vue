<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createOrder, getActivity, getSeats } from '../api'
import type { Activity, Seat, Session } from '../types'
const props=defineProps<{activity:Activity}>(); const emit=defineEmits<{back:[]}>()
const sessions=ref<Session[]>([]), selectedSession=ref(''), seats=ref<Seat[]>([]), selected=ref<string[]>([]), loading=ref(true), error=ref(''), order=ref<Record<string,string>|null>(null)
const currentSession=computed(()=>sessions.value.find(s=>s.id===selectedSession.value))
async function loadSeats(){if(!selectedSession.value)return;loading.value=true;error.value='';selected.value=[];try{seats.value=await getSeats(selectedSession.value)}catch{error.value='座位数据加载失败，请稍后重试。'}finally{loading.value=false}}
function toggle(seat:Seat){if(!['AVAILABLE','available'].includes(seat.status))return;selected.value=selected.value.includes(seat.id)?selected.value.filter(id=>id!==seat.id):selected.value.length<6?[...selected.value,seat.id]:selected.value}
async function submit(){if(!selected.value.length)return;try{order.value=await createOrder(selectedSession.value,selected.value,crypto.randomUUID())}catch{error.value='订单创建失败，请稍后重试。'}}
onMounted(async()=>{try{const d=await getActivity(props.activity.id);sessions.value=d.sessions;selectedSession.value=sessions.value[0]?.id||'';await loadSeats()}catch{error.value='场次数据加载失败，请稍后重试。'}finally{loading.value=false}})
</script>
<template><section class="booking-panel"><button class="secondary-btn" @click="emit('back')">← 返回活动</button><p class="eyebrow">{{activity.organizer}}</p><h1>{{activity.title}}</h1><label class="muted-label">选择场次<select v-model="selectedSession" @change="loadSeats"><option v-for="s in sessions" :key="s.id" :value="s.id">{{new Date(s.startsAt).toLocaleString('zh-CN')}} · {{s.venue}}</option></select></label><p v-if="currentSession" class="event-meta">{{currentSession.status}}</p><p v-if="error" class="alert">{{error}}</p><p v-if="loading">正在加载座位...</p><div v-else class="seat-grid"><button v-for="seat in seats" :key="seat.id" class="seat" :class="{selected:selected.includes(seat.id),locked:!['AVAILABLE','available'].includes(seat.status)}" :disabled="!['AVAILABLE','available'].includes(seat.status)" @click="toggle(seat)">{{seat.row}}{{seat.number}}</button></div><button class="primary-btn" :disabled="!selected.length||!!order" @click="submit">{{order?'订单已创建':'确认选座（'+selected.length+'）'}}</button><pre v-if="order" class="order-result">订单号：{{order.id}}<br>状态：{{order.status}}</pre></section></template>
