import type { OnlineOrder, OnlinePaymentResult } from '../api/fees'

/** Razorpay's payment window. Loaded only when someone pays online, never on other pages. */
const CHECKOUT_URL = 'https://checkout.razorpay.com/v1/checkout.js'

interface CheckoutResponse {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

interface Checkout {
  open(): void
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => Checkout
  }
}

let loading: Promise<void> | null = null

function loadCheckout(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve()
  }
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      loading = null
      script.remove()
      reject(new Error('Could not load the Razorpay payment window. Check the internet connection and try again.'))
    }
    document.body.appendChild(script)
  })
  return loading
}

/** The payment window was closed without paying. */
export class PaymentCancelled extends Error {}

/**
 * Opens the payment window for a server-created order. Resolves with what the
 * server must verify; rejects with {@link PaymentCancelled} if it is closed.
 * A failed attempt stays inside the window so the parent can retry.
 */
export async function payWithRazorpay(order: OnlineOrder, color: string): Promise<OnlinePaymentResult> {
  await loadCheckout()
  const Razorpay = window.Razorpay
  if (!Razorpay) {
    throw new Error('The Razorpay payment window did not load')
  }
  return new Promise<OnlinePaymentResult>((resolve, reject) => {
    new Razorpay({
      key: order.keyId,
      order_id: order.orderId,
      amount: order.amountPaise,
      currency: order.currency,
      name: order.schoolName,
      description: order.description,
      prefill: { name: order.studentName ?? undefined, contact: order.contact ?? undefined },
      theme: { color },
      handler: (r: CheckoutResponse) =>
        resolve({
          razorpayOrderId: r.razorpay_order_id,
          razorpayPaymentId: r.razorpay_payment_id,
          razorpaySignature: r.razorpay_signature,
        }),
      modal: { ondismiss: () => reject(new PaymentCancelled('Payment window closed')) },
    }).open()
  })
}
