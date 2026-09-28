<script setup lang="ts">
import { ChevronDown, LayoutDashboard, ListOrdered, LogOut, Ticket, UserRound } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import LoginView from './views/LoginView.vue'
import AdminLayout from './views/admin/AdminLayout.vue'
import BookingView from './views/BookingView.vue'
import OrdersView from './views/OrdersView.vue'
import PaymentView from './views/PaymentView.vue'
import ProfileView from './views/ProfileView.vue'
import { getProfile, logout as logoutApi, session, type UserProfile } from './auth/authApi'
import { canAccessAdmin, tokenClaims } from './auth/tokenClaims'
import { userMenuItems } from './auth/navigationPermissions'
import { activityTitle } from './views/activityList'
import { listActivities } from './api'
import type { Activity } from './types'

const activities = ref<Activity[]>([])
const loading = ref(true)
const error = ref('')
const signedIn = ref(false)
const canAdmin = ref(false)
const admin = ref(false)
const orders = ref(false)
const profile = ref(false)
const accountMenuOpen = ref(false)
const account = ref<UserProfile | null>(null)
const permissions = ref<string[]>([])
const selectedActivity = ref<Activity | null>(null)
const paymentOrderId = ref('')
const page = ref(0)
const total = ref(0)
const keyword = ref('')
const organizer = ref('')
const pageSize = 12

const initials = () => (account.value?.nickname || account.value?.email || account.value?.phone || '用户').trim().slice(0, 1).toUpperCase()
const accountName = () => account.value?.nickname || account.value?.email || account.value?.phone || '我的账户'
const roleLabel = () => canAdmin.value ? '管理权限' : '普通用户'
const accountMenuItems = computed(() => userMenuItems.filter(item => item.id !== 'home' && !(canAdmin.value && item.id === 'orders')))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await listActivities({ keyword: keyword.value, organizer: organizer.value, page: page.value, size: pageSize })
    activities.value = result.items
    total.value = result.total
  } catch {
    error.value = '活动列表暂不可用，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 0
  load()
}

async function loadAccount() {
  try {
    account.value = await getProfile()
  } catch {
    account.value = null
  }
}

function loggedIn() {
  signedIn.value = true
  const claims = tokenClaims(session.token() || '')
  permissions.value = claims.permissions || []
  canAdmin.value = canAccessAdmin(claims)
  admin.value = false
  loadAccount()
  load()
}

function closeViews() {
  accountMenuOpen.value = false
  orders.value = false
  profile.value = false
  admin.value = false
  selectedActivity.value = null
  paymentOrderId.value = ''
}

function openMenuItem(id: string) {
  accountMenuOpen.value = false
  closeViews()
  if (id === 'orders') orders.value = true
  if (id === 'profile') profile.value = true
}

function openAdmin() {
  if (!canAdmin.value) return
  accountMenuOpen.value = false
  closeViews()
  admin.value = true
}

function returnFromProfile() {
  const shouldReturnToAdmin = canAdmin.value
  closeViews()
  admin.value = shouldReturnToAdmin
}

function startPayment(orderId: string) {
  paymentOrderId.value = orderId
}

async function logout() {
  await logoutApi()
  resetUi()
}

function resetUi() {
  session.clear()
  signedIn.value = false
  canAdmin.value = false
  accountMenuOpen.value = false
  account.value = null
  permissions.value = []
  closeViews()
}

onMounted(load)
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand brand-button" type="button" aria-label="返回活动首页" @click="closeViews">
        <span class="brand-mark"><Ticket :size="17" /></span>
        <span>Cloud Ticketing</span>
      </button>

      <div v-if="signedIn" class="account-menu-wrap">
        <button class="account-trigger" data-testid="account-menu-trigger" type="button" :aria-expanded="accountMenuOpen" aria-controls="account-menu" @click="accountMenuOpen=!accountMenuOpen">
          <img v-if="account?.avatarUrl" :src="account.avatarUrl" alt="账户头像" class="topbar-avatar">
          <span v-else class="topbar-avatar topbar-avatar-fallback" aria-hidden="true">{{ initials() }}</span>
          <span class="account-trigger-copy"><strong>{{ accountName() }}</strong><small>{{ roleLabel() }}</small></span>
          <ChevronDown class="account-trigger-chevron" :size="16" :class="{ open: accountMenuOpen }" aria-hidden="true" />
        </button>

        <div v-if="accountMenuOpen" id="account-menu" class="account-menu" data-testid="account-menu" role="menu">
          <div class="account-menu-summary"><span class="account-menu-name">{{ accountName() }}</span><span class="account-role-badge" :class="{ admin: canAdmin }">{{ roleLabel() }}</span></div>
          <button v-for="item in accountMenuItems" :key="item.id" :data-testid="item.id === 'profile' ? 'profile-nav' : undefined" type="button" role="menuitem" @click="openMenuItem(item.id)">
            <ListOrdered v-if="item.id === 'orders'" :size="17" aria-hidden="true" />
            <UserRound v-else :size="17" aria-hidden="true" />
            {{ item.label }}
          </button>
          <button v-if="canAdmin" class="account-menu-admin" data-testid="admin-menu-entry" type="button" role="menuitem" @click="openAdmin"><LayoutDashboard :size="17" aria-hidden="true" /> 管理后台</button>
          <div class="account-menu-divider"></div>
          <button class="account-menu-logout" type="button" role="menuitem" @click="logout"><LogOut :size="17" aria-hidden="true" /> 退出登录</button>
        </div>
      </div>
    </header>

    <main class="content">
      <LoginView v-if="!signedIn" @done="loggedIn" />
      <template v-else>
        <AdminLayout v-if="admin" :permissions="permissions" />
        <ProfileView v-else-if="profile" @back="returnFromProfile" @password-changed="resetUi" />
        <PaymentView v-else-if="paymentOrderId" :order-id="paymentOrderId" @back="paymentOrderId=''" @done="paymentOrderId='';orders=true" />
        <OrdersView v-else-if="orders" @back="closeViews" @pay="startPayment" />
        <BookingView v-else-if="selectedActivity" :activity="selectedActivity" @back="selectedActivity=null" @pay="startPayment" />
        <template v-else>
          <section class="event-head"><div><p class="eyebrow">LIVE EVENTS</p><h1>活动与场次</h1></div></section>
          <div class="activity-filters"><input v-model="keyword" data-testid="filter-keyword" placeholder="按活动名称筛选" @keyup.enter="search"><input v-model="organizer" data-testid="filter-organizer" placeholder="按主办方筛选" @keyup.enter="search"><button class="primary-btn compact" @click="search">筛选</button></div>
          <p v-if="error" class="alert">{{ error }}</p>
          <p v-if="loading">正在加载活动...</p>
          <template v-else><section class="activity-grid"><article v-for="activity in activities" :key="activity.id" class="activity-card"><img v-if="activity.coverImageUrl" :src="activity.coverImageUrl" :alt="activity.title" class="activity-cover"><div><p class="eyebrow">正在售票</p><h3>{{ activityTitle(activity) }}</h3><p class="activity-organizer">{{ activity.organizer }}</p><p v-if="activity.description" class="activity-description">{{ activity.description }}</p></div><button class="primary-btn" @click="selectedActivity=activity">查看场次与选座</button></article></section><p v-if="!activities.length" class="activity-empty">没有符合条件的活动。</p><div class="pagination"><button class="secondary-btn" :disabled="page===0" @click="page--;load()">上一页</button><span>第 {{ page + 1 }} 页 · 共 {{ total }} 条</span><button class="secondary-btn" :disabled="(page + 1) * pageSize >= total" @click="page++;load()">下一页</button></div></template>
        </template>
      </template>
    </main>
  </div>
</template>
