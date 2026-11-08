// Schemas of docs/spec.md Section 2 as returned by the API.

export interface Money {
  amountMinor: number
  currency: string
}

export interface Address {
  line1: string
  city: string
  instructions?: string | null
}

export interface Page<T> {
  items: T[]
  nextCursor: string | null
}

export interface Restaurant {
  id: string
  name: string
  available: boolean
}

export interface MenuItem {
  id: string
  restaurantId: string
  name: string
  price: Money
  available: boolean
  version: number
}

export interface OrderItem {
  menuItemId: string
  name: string
  quantity: number
  unitPrice: Money
}

export interface Quote {
  id: string
  restaurantId: string
  items: OrderItem[]
  total: Money
  expiresAt: string
  quoteToken: string
}

export type OrderStatus =
  | 'CREATED'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'ON_THE_WAY'
  | 'DELIVERED'
  | 'REJECTED'
  | 'PAYMENT_FAILED'

export type PaymentStatus = 'PENDING' | 'UNKNOWN' | 'AUTHORIZED' | 'FAILED' | 'REFUND_PENDING' | 'REFUNDED'

export interface Order {
  id: string
  customerId: string
  restaurantId: string
  items: OrderItem[]
  total: Money
  deliveryAddress: Address
  status: OrderStatus
  paymentStatus: PaymentStatus
  correlationId: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface OrderReceipt {
  orderId: string
  status: 'CREATED'
  correlationId: string
}

export interface OrderHistoryEntry {
  previousStatus: OrderStatus | null
  status: OrderStatus
  previousPaymentStatus: PaymentStatus | null
  paymentStatus: PaymentStatus
  version: number
  causedBy: string | null
  occurredAt: string
}

export type RestaurantOrderStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'PREPARING'

export interface RestaurantOrder {
  orderId: string
  restaurantId: string
  items: OrderItem[]
  status: RestaurantOrderStatus
  reason: string | null
  version: number
}

export interface Driver {
  id: string
  profileId: string
  onDuty: boolean
  activeDeliveryId: string | null
  version: number
}

export type DeliveryStatus = 'UNASSIGNED' | 'ASSIGNED' | 'PICKED_UP' | 'DELIVERED'

export interface Delivery {
  id: string
  orderId: string
  restaurantId: string
  driverId: string | null
  deliveryAddress: Address
  status: DeliveryStatus
  version: number
  createdAt: string
  updatedAt: string
}
