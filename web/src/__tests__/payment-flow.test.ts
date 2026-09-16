import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PaymentView from '../views/PaymentView.vue'
import { session } from '../auth/authApi'
import { createPayment, payOrder } from '../api'

const intent = {
  paymentId: 'p1', orderId: 'o1', method: 'WECHAT', amountMinor: 20000, currency: 'CNY',
  status: 'PENDING', orderStatus: 'PENDING', seatCount: 2,
  qrContent: 'cloudticket://pay?order=o1', qrCode: 'data:image/png;base64,AAA',
  expiresAt: new Date(Date.now() + 600000).toISOString(),
}

describe('payment flow', () => {
  beforeEach(() => { session.set('token'); vi.stubGlobal('fetch', vi.fn()) })
  afterEach(() => { session.clear(); vi.unstubAllGlobals() })

  it('creates a payment intent for the selected method', async () => {
    ;(fetch as any).mockResolvedValueOnce(new Response(JSON.stringify(intent), { status: 200 }))

    const result = await createPayment('o1', 'ALIPAY')

    expect(fetch).toHaveBeenCalledWith('/api/orders/o1/payments', expect.objectContaining({ method: 'POST' }))
    expect(result.qrCode).toBe('data:image/png;base64,AAA')
    expect(result.amountMinor).toBe(20000)
  })

  it('completes the simulated payment', async () => {
    ;(fetch as any).mockResolvedValueOnce(new Response(JSON.stringify({ ...intent, status: 'SUCCESS' }), { status: 200 }))

    const result = await payOrder('o1')

    expect(fetch).toHaveBeenCalledWith('/api/orders/o1/pay', expect.objectContaining({ method: 'POST' }))
    expect(result.status).toBe('SUCCESS')
  })

  it('shows the qr code and finishes payment from the view', async () => {
    ;(fetch as any).mockImplementation((url: string) => Promise.resolve(
      new Response(JSON.stringify(url.endsWith('/pay') ? { ...intent, status: 'SUCCESS' } : intent), { status: 200 })))
    const wrapper = mount(PaymentView, { props: { orderId: 'o1' } })
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.find('[data-testid="payment-qr"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('微信支付')
    expect(wrapper.text()).toContain('200.00')

    await wrapper.get('[data-testid="pay-now"]').trigger('click')
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.text()).toContain('支付成功')
  })
})
