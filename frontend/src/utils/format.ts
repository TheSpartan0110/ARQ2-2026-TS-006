import type { DeliveryStatus, Money, OrderStatus, PaymentStatus, RestaurantOrderStatus } from '../types/domain'

const copFormatter = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

/** Money travels in minor units (Spec 2.1): COP 25,000 is 2500000. */
export function formatMoney(money: Money): string {
  return copFormatter.format(money.amountMinor / 100)
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export type Tone = 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'danger'

interface StatusStyle {
  label: string
  tone: Tone
}

export const ORDER_STATUS: Record<OrderStatus, StatusStyle> = {
  CREATED: { label: 'Created', tone: 'info' },
  ACCEPTED: { label: 'Accepted', tone: 'progress' },
  PREPARING: { label: 'Preparing', tone: 'progress' },
  ON_THE_WAY: { label: 'On the way', tone: 'progress' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
  PAYMENT_FAILED: { label: 'Payment failed', tone: 'danger' },
}

export const PAYMENT_STATUS: Record<PaymentStatus, StatusStyle> = {
  PENDING: { label: 'Payment pending', tone: 'neutral' },
  UNKNOWN: { label: 'Payment being confirmed', tone: 'warning' },
  AUTHORIZED: { label: 'Paid', tone: 'success' },
  FAILED: { label: 'Payment declined', tone: 'danger' },
  REFUND_PENDING: { label: 'Refund in progress', tone: 'warning' },
  REFUNDED: { label: 'Refunded', tone: 'info' },
}

export const RESTAURANT_ORDER_STATUS: Record<RestaurantOrderStatus, StatusStyle> = {
  PENDING: { label: 'New', tone: 'warning' },
  ACCEPTED: { label: 'Accepted', tone: 'progress' },
  PREPARING: { label: 'Preparing', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
}

export const DELIVERY_STATUS: Record<DeliveryStatus, StatusStyle> = {
  UNASSIGNED: { label: 'Waiting for a driver', tone: 'neutral' },
  ASSIGNED: { label: 'Assigned: pick it up', tone: 'warning' },
  PICKED_UP: { label: 'On the way', tone: 'progress' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
}

export const TONE_CLASSES: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-700 border-slate-200',
  info: 'bg-sky-50 text-sky-700 border-sky-200',
  progress: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  success: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  warning: 'bg-amber-50 text-amber-800 border-amber-200',
  danger: 'bg-rose-50 text-rose-700 border-rose-200',
}

/** Final states never change again (Spec 2.4), so screens stop polling once an order reaches them. */
export function isFinal(status: OrderStatus, payment: PaymentStatus): boolean {
  if (status === 'DELIVERED' || status === 'PAYMENT_FAILED') return true
  return status === 'REJECTED' && payment === 'REFUNDED'
}
