import http from './http'

/** 供应链集中度风险（赫芬达尔指数） */
export function fetchConcentration(materialName) {
  return http
    .get('/graph/concentration', { params: { materialName } })
    .then((r) => r.data)
}

/** 替代供应商发现 */
export function fetchAlternativeSuppliers(supplierId) {
  return http.get(`/graph/alternative/${supplierId}`).then((r) => r.data)
}

/** 关联风险穿透 */
export function fetchRelationRisk(supplierId, maxDepth = 3) {
  return http
    .get(`/graph/relation-risk/${supplierId}`, { params: { maxDepth } })
    .then((r) => r.data)
}

/** 供应商综合评分 */
export function fetchSupplierScore(supplierId) {
  return http.get(`/graph/supplier-score/${supplierId}`).then((r) => r.data)
}

/** 采购员画像 */
export function fetchBuyerProfile(userId) {
  return http.get(`/graph/buyer-profile/${userId}`).then((r) => r.data)
}
