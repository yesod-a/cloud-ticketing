<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { createPayment, payOrder, type PaymentIntent } from '../api'

const props = defineProps<{ orderId: string }>()
const emit = defineEmits<{ back: []; done: [] }>()

const methods = [
  { code: 'WECHAT', label: '微信支付', hint: '扫码后点击下方按钮完成支付' },
  { code: 'ALIPAY', label: '支付宝', hint: '扫码后点击下方按钮完成支付' },
  { code: 'UNIONPAY', label: '云闪付', hint: '扫码后点击下方按钮完成支付' },
]
const method = ref('WECHAT'), payment = ref<PaymentIntent | null>(null), loading = ref(false), error = ref(''), paid = ref(false), remaining = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

const amount = computed(() => ((payment.value?.amountMinor ?? 0) / 100).toFixed(2))
const countdown = computed(() => {
  if (remaining.value <= 0) return '已超时'
  const minutes = Math.floor(remaining.value / 60), seconds = remaining.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

async function load() {
  loading.value = true; error.value = ''
  try { payment.value = await createPayment(props.orderId, method.value); tick() } catch { error.value = '支付单创建失败，请稍后重试。' } finally { loading.value = false }
}
async function select(code: string) { method.value = code; await load() }
async function pay() {
  loading.value = true; error.value = ''
  try { payment.value = await payOrder(props.orderId); paid.value = true; emit('done') } catch { error.value = '支付失败，订单可能已超时或已取消。' } finally { loading.value = false }
}
function tick() {
  if (timer) clearInterval(timer)
  const compute = () => {
    if (!payment.value?.expiresAt) { remaining.value = 0; return }
    remaining.value = Math.max(0, Math.floor((new Date(payment.value.expiresAt).getTime() - Date.now()) / 1000))
  }
  compute(); timer = setInterval(compute, 1000)
}
onMounted(load)
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section class="payment-panel">
    <div class="admin-heading"><div><p class="eyebrow">PAYMENT</p><h1>订单支付</h1></div><button class="secondary-btn" @click="emit('back')">返回</button></div>
    <p v-if="error" class="alert">{{ error }}</p>
    <div v-if="paid" class="payment-success">
      <div class="success-icon">✓</div>
      <h2>支付成功</h2>
      <p>订单 {{ orderId }} 已进入待出票阶段。</p>
      <button class="primary-btn" @click="emit('back')">查看我的订单</button>
    </div>
    <div v-else class="payment-grid">
      <div class="payment-methods">
        <p class="muted-label">支付方式</p>
        <button v-for="item in methods" :key="item.code" type="button" class="method-card" :class="{ active: method === item.code }" @click="select(item.code)">
          <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
        </button>
            <div class="payment-meta"><span>订单号</span><code>{{ orderId }}</code></div>
        <div class="payment-meta"><span>座位数量</span><span>{{ payment?.seatCount ?? 0 }} 个</span></div>
        <div class="payment-meta"><span>应付金额</span><strong class="amount">¥{{ amount }}</strong></div>
        <div class="payment-meta"><span>剩余时间</span><span>{{ countdown }}</span></div>
      </div>
      <div class="payment-qr">
        <p class="muted-label">扫码支付（模拟）</p>
        <p v-if="loading">正在生成支付二维码...</p>
        <img v-else-if="payment?.qrCode" :src="payment.qrCode" alt="支付二维码" data-testid="payment-qr">
        <p v-else class="muted">暂无可用的支付二维码。</p>
        <p class="qr-note">这是模拟支付，不会产生真实扣款。</p>
        <button class="primary-btn" data-testid="pay-now" :disabled="loading || !payment?.qrCode" @click="pay">我已支付，完成</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.payment-panel{margin-bottom:30px}.payment-grid{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:24px;margin-top:20px}.payment-methods,.payment-qr{border:1px solid #253044;background:#111927;border-radius:10px;padding:20px}.method-card{display:grid;gap:4px;width:100%;text-align:left;border:1px solid #31405a;background:#151f31;color:#dce5f1;border-radius:8px;padding:12px 14px;margin-bottom:10px;cursor:pointer;font:inherit}.method-card.active{border-color:#43d5b3;background:#16281f}.method-card small{color:#8292ab;font-size:11px}.payment-meta{display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-top:1px solid #1f2a3a;color:#8b98ab;font-size:13px}.payment-meta code{color:#dce5f1;font-size:11px;word-break:break-all}.amount{color:#f2ab5c;font-size:20px}.payment-qr{display:grid;justify-items:center;gap:12px;align-content:start}.payment-qr img{width:240px;height:240px;background:#fff;border-radius:10px;padding:8px}.qr-note{color:#67758b;font-size:11px;text-align:center}.payment-success{display:grid;place-items:center;gap:10px;text-align:center;padding:40px 0}.payment-success h2{margin:0}@media(max-width:860px){.payment-grid{grid-template-columns:1fr}}
</style>
