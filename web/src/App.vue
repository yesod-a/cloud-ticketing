<script setup lang="ts">
import { onMounted, ref } from 'vue'
import LoginView from './views/LoginView.vue'
import AdminLayout from './views/admin/AdminLayout.vue'
import BookingView from './views/BookingView.vue'
import OrdersView from './views/OrdersView.vue'
import PaymentView from './views/PaymentView.vue'
import { api, logout as logoutApi, session } from './auth/authApi'
import { canAccessAdmin, tokenClaims } from './auth/tokenClaims'
import { activityTitle } from './views/activityList'
type Activity={id:string,title:string,organizer:string,status:string}
const activities=ref<Activity[]>([]),loading=ref(true),error=ref(''),signedIn=ref(false),admin=ref(false),orders=ref(false),permissions=ref<string[]>([]),selectedActivity=ref<Activity|null>(null),paymentOrderId=ref('')
async function load(){loading.value=true;error.value='';try{activities.value=await api<Activity[]>('/api/activities')}catch{error.value='活动列表暂不可用，请稍后重试。'}finally{loading.value=false}}
function loggedIn(){signedIn.value=true;const claims=tokenClaims(session.token()||'');permissions.value=claims.permissions||[];admin.value=canAccessAdmin(claims);load()}
function startPayment(orderId:string){paymentOrderId.value=orderId}
async function logout(){await logoutApi();signedIn.value=false;admin.value=false;orders.value=false;permissions.value=[];selectedActivity.value=null;paymentOrderId.value=''}
onMounted(load)
</script>
<template><div class="app-shell"><header class="topbar"><div class="brand">Cloud Ticketing</div><nav><button v-if="signedIn" class="icon-btn" @click="orders=!orders;admin=false;selectedActivity=null;paymentOrderId=''">我的订单</button><button v-if="signedIn && admin" class="icon-btn" @click="admin=!admin;orders=false;paymentOrderId=''">管理</button><button v-if="signedIn" class="icon-btn" @click="logout">退出</button></nav></header><main class="content"><LoginView v-if="!signedIn" @done="loggedIn"/><template v-else><AdminLayout v-if="admin" :permissions="permissions"/><PaymentView v-else-if="paymentOrderId" :order-id="paymentOrderId" @back="paymentOrderId=''" @done="paymentOrderId='';orders=true"/><OrdersView v-else-if="orders" @back="orders=false" @pay="startPayment"/><BookingView v-else-if="selectedActivity" :activity="selectedActivity" @back="selectedActivity=null" @pay="startPayment"/><template v-else><section class="event-head"><div><p class="eyebrow">LIVE EVENTS</p><h1>活动与场次</h1></div></section><p v-if="error" class="alert">{{error}}</p><p v-if="loading">正在加载活动...</p><section v-else class="activity-grid"><article v-for="activity in activities" :key="activity.id" class="summary-panel"><p class="eyebrow">{{activity.status}}</p><h2>{{activityTitle(activity)}}</h2><p>{{activity.organizer}}</p><button class="primary-btn" @click="selectedActivity=activity">查看场次与选座</button></article></section></template></template></main></div></template>
