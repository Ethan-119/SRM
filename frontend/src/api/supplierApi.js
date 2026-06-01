import http from './http'

export function fetchSupplierList() {
  return http.get('/supplier').then((r) => r.data)
}

export function fetchSupplierById(id) {
  return http.get(`/supplier/${id}`).then((r) => r.data)
}

export function createSupplier(payload) {
  return http.post('/supplier', payload).then((r) => r.data)
}

export function updateSupplier(id, payload) {
  return http.put(`/supplier/${id}`, payload).then((r) => r.data)
}

export function deleteSupplier(id) {
  return http.delete(`/supplier/${id}`).then((r) => r.data)
}

export function fetchSupplierPage(params = {}) {
  return http.get('/supplier/page', { params }).then((r) => r.data)
}
