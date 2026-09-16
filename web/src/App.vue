<script setup lang="ts">
import { onMounted, ref } from 'vue'
import LoginView from './views/LoginView.vue'
import AdminLayout from './views/admin/AdminLayout.vue'
import BookingView from './views/BookingView.vue'
import OrdersView from './views/OrdersView.vue'
import PaymentView from './views/PaymentView.vue'
import { logout as logoutApi, session } from './auth/authApi'
import { canAccessAdmin, tokenClaims } from './auth/tokenClaims'
import { activityTitle } from './views/activityList'
import { listActivities } from './api'
type Activity={id:string,title:string,organizer:string,status:string}
const activities=ref<Activity[]>([]),loading=ref(true),error=ref(''),signedIn=ref(false),admin=ref(false),orders=ref(false),permissions=ref<string[]>([]),selectedActivity=ref<Activity|null>(null),paymentOrderId=ref('')
const page=ref(0),total=ref(0),keyword=ref(''),organizer=ref('')
const pageSize=12
async function load(){loading.value=true;error.value='';try{const result=await listActivities({keyword:keyword.value,organizer:organizer.value,page:page.value,size:pageSize});activities.value=result.items;total.value=result.total}catch{error.value='活动列表暂不可用，请稍后重试。'}finally{loading.value=false}}
function search(){page.value=0;load()}
function loggedIn(){signedIn.value=true;const claims=tokenClaims(session.token()||'');permissions.value=claims.permissions||[];admin.value=canAccessAdmin(claims);load()}
function startPayment(orderId:string){paymentOrderId.value=orderId}
async function logout(){await logoutApi();signedIn.value=false;admin.value=false;orders.value=false;permissions.value=[];selectedActivity.value=null;paymentOrderId.value=''}
onMounted(load)
</script>
<template><div class="app-shell"><header class="topbar"><div class="brand">Cloud Ticketing</div><nav><button v-if="signedIn" class="icon-btn" @click="orders=!orders;admin=false;selectedActivity=null;paymentOrderId=''">我的订单</button><button v-if="signedIn && admin" class="icon-btn" @click="admin=!admin;orders=false;paymentOrderId=''">管理</button><button v-if="signedIn" class="icon-btn" @click="logout">退出</button></nav></header><main class="content"><LoginView v-if="!signedIn" @done="loggedIn"/><template v-else><AdminLayout v-if="admin" :permissions="permissions"/><PaymentView v-else-if="paymentOrderId" :order-id="paymentOrderId" @back="paymentOrderId=''" @done="paymentOrderId='';orders=true"/><OrdersView v-else-if="orders" @back="orders=false" @pay="startPayment"/><BookingView v-else-if="selectedActivity" :activity="selectedActivity" @back="selectedActivity=null" @pay="startPayment"/><template v-else><section class="event-head"><div><p class="eyebrow">LIVE EVENTS</p><h1>活动与场次</h1></div></section><div class="activity-filters"><input v-model="keyword" data-testid="filter-keyword" placeholder="按活动名称筛选" @keyup.enter="search"><input v-model="organizer" data-testid="filter-organizer" placeholder="按主办方筛选" @keyup.enter="search"><button class="primary-btn compact" @click="search">筛选</button></div><p v-if="error" class="alert">{{error}}</p><p v-if="loading">正在加载活动...</p><template v-else><section class="activity-grid"><article v-for="activity in activities" :key="activity.id" class="activity-card"><div><p class="eyebrow">正在售票</p><h3>{{activityTitle(activity)}}</h3><p class="activity-organizer">{{activity.organizer}}</p></div><button class="primary-btn" @click="selectedActivity=activity">查看场次与选座</button></article></section><p v-if="!activities.length" class="activity-empty">没有符合条件的活动。</p><div class="pagination"><button class="secondary-btn" :disabled="page===0" @click="page--;load()">上一页</button><span>第 {{page+1}} 页 · 共 {{total}} 条</span><button class="secondary-btn" :disabled="(page+1)*pageSize>=total" @click="page++;load()">下一页</button></div></template></template></template></main></div></template>
