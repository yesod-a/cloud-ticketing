<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { cancelOrder, myOrders, requestOrderRefund, type UserOrder } from '../adminApi'
const emit=defineEmits<{back:[]}>(); const rows=ref<UserOrder[]>([]),page=ref(0),total=ref(0),loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{const result=await myOrders({page:page.value,size:10});rows.value=result.items;total.value=result.total}catch{error.value='订单加载失败，请稍后重试。'}finally{loading.value=false}}
async function cancel(id:string){try{await cancelOrder(id);await load()}catch{error.value='订单当前状态不可取消。'}}
async function refund(id:string){const reason=window.prompt('请输入退款原因');if(!reason?.trim())return;try{await requestOrderRefund(id,reason);await load()}catch{error.value='退款申请提交失败。'}}
onMounted(load)
</script>
<template><section class="orders-panel"><div class="admin-heading"><div><p class="eyebrow">ACCOUNT</p><h1>我的订单</h1></div><button class="secondary-btn" @click="emit('back')">返回活动</button></div><p v-if="error" class="alert">{{error}}</p><p v-if="loading">正在加载订单...</p><table v-else class="admin-table"><thead><tr><th>订单号</th><th>场次</th><th>座位</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{row.id}}</td><td>{{row.sessionId}}</td><td>{{row.seatIds}}</td><td>{{row.status}}</td><td><button v-if="['PENDING','PAID'].includes(row.status)" class="icon-btn" @click="cancel(row.id)">取消</button><button v-if="['PENDING','PAID'].includes(row.status)" class="icon-btn" @click="refund(row.id)">申请退款</button></td></tr><tr v-if="!rows.length"><td colspan="5">暂无订单</td></tr></tbody></table><div class="pagination"><button class="secondary-btn" :disabled="page===0" @click="page--;load()">上一页</button><span>第 {{page+1}} 页 · 共 {{total}} 条</span><button class="secondary-btn" :disabled="(page+1)*10>=total" @click="page++;load()">下一页</button></div></section></template>
