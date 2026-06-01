import http from './http'

export function fetchOrderList() {
  return http.get('/order').then((r) => r.data)
}

export function fetchOrderById(id) {
  return http.get(`/order/${id}`).then((r) => r.data)
}

export function createOrder(payload) {
  return http.post('/order', payload).then((r) => r.data)
}

export function updateOrder(id, payload) {
  return http.put(`/order/${id}`, payload).then((r) => r.data)
}

export function deleteOrder(id) {
  return http.delete(`/order/${id}`).then((r) => r.data)
}

export function fetchOrderPage(params = {}) {
  return http.get('/order/page', { params }).then((r) => r.data)
}
