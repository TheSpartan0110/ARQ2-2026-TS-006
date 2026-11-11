import type {
  Address,
  Delivery,
  DeliveryStatus,
  Driver,
  MenuItem,
  Order,
  OrderHistoryEntry,
  OrderReceipt,
  Page,
  Quote,
  Restaurant,
  RestaurantOrder,
  RestaurantOrderStatus,
} from '../../types/domain'
import { api, apiRequest, type ApiResponse } from './http'

/** Catalog (Spec 3.3): any active role reads; quotes are for customers. */
export const catalogApi = {
  restaurants: (token: string) => api<Page<Restaurant>>('/api/v1/restaurants?limit=100', { token }),

  menu: (token: string, restaurantId: string) =>
    api<Page<MenuItem>>(`/api/v1/restaurants/${restaurantId}/menu?limit=100`, { token }),

  quote: (token: string, restaurantId: string, items: { menuItemId: string; quantity: number }[]) =>
    api<Quote>('/api/v1/quotes', { method: 'POST', token, body: { restaurantId, items }, idempotent: true }),
}

/** Orders (Spec 3.4): the customer's own orders. */
export const ordersApi = {
  /** Returns the receipt plus the trace id of the creation request. */
  create: (token: string, quoteToken: string, deliveryAddress: Address, paymentMethodToken: string)
    : Promise<ApiResponse<OrderReceipt>> =>
    apiRequest<OrderReceipt>('/api/v1/orders', {
      method: 'POST',
      token,
      body: { quoteToken, deliveryAddress, paymentMethodToken },
      idempotent: true,
    }),

  list: (token: string, limit = 10) => api<Page<Order>>(`/api/v1/orders?limit=${limit}`, { token }),

  get: (token: string, orderId: string) => api<Order>(`/api/v1/orders/${orderId}`, { token }),

  history: (token: string, orderId: string) =>
    api<OrderHistoryEntry[]>(`/api/v1/orders/${orderId}/history`, { token }),
}

/** Restaurant inbox and decisions (Spec 3.5). */
export const restaurantApi = {
  inbox: (token: string, status?: RestaurantOrderStatus) =>
    api<Page<RestaurantOrder>>(`/api/v1/restaurant-orders?limit=100${status ? `&status=${status}` : ''}`, { token }),

  accept: (token: string, orderId: string, expectedVersion: number) =>
    api<RestaurantOrder>(`/api/v1/restaurant-orders/${orderId}/accept`, {
      method: 'POST', token, body: { expectedVersion }, idempotent: true,
    }),

  reject: (token: string, orderId: string, expectedVersion: number, reason: string) =>
    api<RestaurantOrder>(`/api/v1/restaurant-orders/${orderId}/reject`, {
      method: 'POST', token, body: { expectedVersion, reason }, idempotent: true,
    }),

  startPreparation: (token: string, orderId: string, expectedVersion: number) =>
    api<RestaurantOrder>(`/api/v1/restaurant-orders/${orderId}/preparation`, {
      method: 'POST', token, body: { expectedVersion }, idempotent: true,
    }),
}

/** Driver duty and deliveries (Spec 3.6). */
export const driverApi = {
  me: (token: string) => api<Driver>('/api/v1/drivers/me', { token }),

  setDuty: (token: string, onDuty: boolean, expectedVersion: number) =>
    api<Driver>('/api/v1/drivers/me/duty', { method: 'PUT', token, body: { onDuty, expectedVersion }, idempotent: true }),

  deliveries: (token: string, status?: DeliveryStatus) =>
    api<Page<Delivery>>(`/api/v1/deliveries?limit=100${status ? `&status=${status}` : ''}`, { token }),

  pickUp: (token: string, deliveryId: string, expectedVersion: number) =>
    api<Delivery>(`/api/v1/deliveries/${deliveryId}/pickup`, {
      method: 'POST', token, body: { expectedVersion }, idempotent: true,
    }),

  complete: (token: string, deliveryId: string, expectedVersion: number) =>
    api<Delivery>(`/api/v1/deliveries/${deliveryId}/completion`, {
      method: 'POST', token, body: { expectedVersion }, idempotent: true,
    }),
}
