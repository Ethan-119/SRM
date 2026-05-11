<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import {
  fetchOrderList,
  createOrder,
  updateOrder,
  deleteOrder,
} from '@/api/orderApi'
import { fetchSupplierList } from '@/api/supplierApi'

const ORDER_STATUS_MAP = {
  0: '待确认',
  1: '生产中',
  2: '已发货',
  3: '已签收',
  4: '已取消',
}

const orders = ref([])
const suppliers = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')

const editingId = ref(null)
const showForm = ref(false)

const emptyForm = () => ({
  orderNo: '',
  supplierId: '',
  materialName: '',
  quantity: 1,
  unitPrice: '',
  totalAmount: '',
  deliveryDate: '',
  status: 0,
  remark: '',
})

const form = reactive(emptyForm())

const isEdit = computed(() => editingId.value != null)

const supplierNameMap = computed(() => {
  const m = {}
  for (const s of suppliers.value) {
    m[s.id] = s.supplierName
  }
  return m
})

function supplierLabel(id) {
  return supplierNameMap.value[id] ?? `ID ${id}`
}

async function loadSuppliers() {
  try {
    suppliers.value = await fetchSupplierList()
  } catch {
    suppliers.value = []
  }
}

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    orders.value = await fetchOrderList()
  } catch (e) {
    error.value = e.message || '加载订单失败'
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadSuppliers(), loadOrders()])
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  showForm.value = true
  success.value = ''
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    orderNo: row.orderNo ?? '',
    supplierId:
      row.supplierId != null && row.supplierId !== ''
        ? String(row.supplierId)
        : '',
    materialName: row.materialName ?? '',
    quantity: row.quantity ?? 1,
    unitPrice:
      row.unitPrice != null ? String(row.unitPrice) : '',
    totalAmount:
      row.totalAmount != null ? String(row.totalAmount) : '',
    deliveryDate: row.deliveryDate ?? '',
    status: row.status ?? 0,
    remark: row.remark ?? '',
  })
  showForm.value = true
  success.value = ''
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
}

function parseDecimal(str) {
  const t = String(str).trim()
  if (!t) return undefined
  const n = Number(t)
  if (Number.isNaN(n)) throw new Error('金额格式不正确')
  return n
}

async function submitForm() {
  error.value = ''
  success.value = ''
  const qty = Number(form.quantity)
  if (!Number.isFinite(qty) || qty <= 0) {
    error.value = '采购数量须为正数'
    return
  }
  let unitPrice
  try {
    unitPrice = parseDecimal(form.unitPrice)
    if (unitPrice === undefined) throw new Error('请填写单价')
  } catch (e) {
    error.value = e.message || '单价无效'
    return
  }
  let totalAmount
  try {
    totalAmount = parseDecimal(form.totalAmount)
  } catch (e) {
    error.value = e.message || '总金额无效'
    return
  }
  if (totalAmount === undefined) {
    totalAmount = Math.round(qty * unitPrice * 100) / 100
  }

  const sid = Number(form.supplierId)
  const payload = {
    orderNo: form.orderNo.trim(),
    supplierId: sid,
    materialName: form.materialName.trim(),
    quantity: qty,
    unitPrice,
    totalAmount,
    deliveryDate: form.deliveryDate || undefined,
    status:
      form.status === '' || form.status == null ? undefined : Number(form.status),
    remark: form.remark?.trim() || undefined,
  }

  if (!Number.isFinite(payload.supplierId)) {
    error.value = '请选择供应商'
    return
  }

  try {
    if (isEdit.value) {
      await updateOrder(editingId.value, payload)
      success.value = '订单已更新'
    } else {
      await createOrder(payload)
      success.value = '订单已创建'
    }
    showForm.value = false
    editingId.value = null
    await loadOrders()
  } catch (e) {
    error.value = e.message || '保存失败'
  }
}

async function remove(row) {
  if (!confirm(`确定删除订单「${row.orderNo}」？`)) return
  error.value = ''
  try {
    await deleteOrder(row.id)
    success.value = '已删除'
    await loadOrders()
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

function orderStatusLabel(v) {
  return ORDER_STATUS_MAP[v] ?? v ?? '—'
}

function orderStatusBadgeClass(v) {
  const map = {
    0: 'badge--amber',
    1: 'badge--violet',
    2: 'badge--sky',
    3: 'badge--emerald',
    4: 'badge--stone',
  }
  return map[v] ?? 'badge--slate'
}

const statPending = computed(
  () => orders.value.filter((o) => o.status === 0).length
)

onMounted(loadAll)
</script>

<template>
  <div class="page portal-customer">
    <div class="stats">
      <div class="stat">
        <b>{{ orders.length }}</b>
        <span>订单总数</span>
      </div>
      <div class="stat">
        <b>{{ suppliers.length }}</b>
        <span>可选供应商</span>
      </div>
      <div class="stat">
        <b>{{ statPending }}</b>
        <span>待确认</span>
      </div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h1>采购订单</h1>
          <p class="lead">
            对接 <code>/api/order</code> · 供应商来自 <code>/api/supplier</code>
          </p>
        </div>
        <div class="toolbar">
          <button type="button" class="btn" :disabled="loading" @click="loadAll">
            刷新
          </button>
          <button type="button" class="btn secondary" @click="openCreate">
            新建订单
          </button>
        </div>
      </div>

      <div v-if="error" class="msg error">{{ error }}</div>
      <div v-if="success" class="msg ok">{{ success }}</div>

      <div v-if="showForm" class="card nested">
        <h2>{{ isEdit ? '编辑订单' : '新建订单' }}</h2>
        <div class="form-grid">
          <label class="field">
            订单编号 *
            <input v-model="form.orderNo" />
          </label>
          <label class="field">
            供应商 *
            <select v-model="form.supplierId">
              <option value="">请选择</option>
              <option v-for="s in suppliers" :key="s.id" :value="String(s.id)">
                {{ s.supplierName }}（{{ s.supplierCode }}）
              </option>
            </select>
          </label>
          <label class="field">
            物料名称 *
            <input v-model="form.materialName" />
          </label>
          <label class="field">
            采购数量 *
            <input v-model.number="form.quantity" type="number" min="1" />
          </label>
          <label class="field">
            单价 *
            <input v-model="form.unitPrice" placeholder="数字" />
          </label>
          <label class="field">
            总金额（可空，默认数量×单价）
            <input v-model="form.totalAmount" placeholder="留空则自动计算" />
          </label>
          <label class="field">
            交货日期
            <input v-model="form.deliveryDate" type="date" />
          </label>
          <label class="field">
            状态
            <select v-model.number="form.status">
              <option :value="0">待确认</option>
              <option :value="1">生产中</option>
              <option :value="2">已发货</option>
              <option :value="3">已签收</option>
              <option :value="4">已取消</option>
            </select>
          </label>
          <label class="field" style="grid-column: 1 / -1">
            备注
            <textarea v-model="form.remark" />
          </label>
        </div>
        <div class="toolbar" style="margin-top: 0.75rem">
          <button type="button" class="btn" @click="submitForm">保存</button>
          <button type="button" class="btn secondary" @click="cancelForm">
            取消
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading-block">
        <span class="loading-spinner" aria-hidden="true" />
        正在拉取订单…
      </div>
      <div v-else-if="orders.length === 0" class="empty-state">
        <span class="emoji">🛒</span>
        暂无订单，点击「新建订单」创建一笔采购。
      </div>
      <div v-else class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>订单号</th>
              <th>供应商</th>
              <th>物料</th>
              <th>数量</th>
              <th>单价</th>
              <th>总额</th>
              <th>交货日</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in orders" :key="row.id">
              <td class="mono">{{ row.orderNo }}</td>
              <td>{{ supplierLabel(row.supplierId) }}</td>
              <td><strong>{{ row.materialName }}</strong></td>
              <td class="mono">{{ row.quantity }}</td>
              <td class="mono">{{ row.unitPrice }}</td>
              <td class="mono">{{ row.totalAmount }}</td>
              <td class="mono">{{ row.deliveryDate || '—' }}</td>
              <td>
                <span class="badge" :class="orderStatusBadgeClass(row.status)">
                  {{ orderStatusLabel(row.status) }}
                </span>
              </td>
              <td class="actions-cell">
                <button type="button" class="btn secondary" @click="openEdit(row)">
                  编辑
                </button>
                <button type="button" class="btn danger" @click="remove(row)">
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
