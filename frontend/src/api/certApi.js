import http from './http'

/** 查询供应商资质证书列表 */
export function fetchSupplierCerts(supplierId) {
  return http.get('/cert', { params: { supplierId } }).then((r) => r.data)
}

/** 供应商上传新资质，触发自动审核（POST + query 参数） */
export function submitCertReview(payload) {
  return http.post('/cert/review', null, { params: payload }).then((r) => r.data)
}
