<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import {
  fetchSupplierPage,
  createSupplier,
  updateSupplier,
  deleteSupplier,
} from '@/api/supplierApi'
import Pagination from '@/components/Pagination.vue'

const STATUS_MAP = {
  0: '注册',
  1: '待审核',
  2: '已准入',
  3: '合作中',
  4: '冻结',
  5: '黑名单',
}

const LEVEL_MAP = { 1: '初级', 2: '中级', 3: '高级' }

const loading = ref(false)
const error = ref('')
const success = ref('')

// --- 分页与筛选 ---
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  supplierName: '',
  status: '',
})

const pageResult = ref({
  pageNum: 1,
  pageSize: 10,
  total: 0,
  records: [],
})

const editingId = ref(null)
const showForm = ref(false)

const emptyForm = () => ({
  supplierCode: '',
  supplierName: '',
  contactPerson: '',
  contactPhone: '',
  email: '',
  region: '',
  mainCategory: '',
  qualificationLevel: '',
  status: 0,
  address: '',
  remark: '',
})

const form = reactive(emptyForm())

const isEdit = computed(() => editingId.value != null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    }
    if (query.supplierName) params.supplierName = query.supplierName
    if (query.status !== '' && query.status != null) params.status = query.status

    pageResult.value = await fetchSupplierPage(params)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function resetSearch() {
  query.supplierName = ''
  query.status = ''
  query.pageNum = 1
  load()
}

function onPageChange(page) {
  query.pageNum = page
  load()
}

function onPageSizeChange(size) {
  query.pageSize = size
  query.pageNum = 1
  load()
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
    supplierCode: row.supplierCode ?? '',
    supplierName: row.supplierName ?? '',
    contactPerson: row.contactPerson ?? '',
    contactPhone: row.contactPhone ?? '',
    email: row.email ?? '',
    region: row.region ?? '',
    mainCategory: row.mainCategory ?? '',
    qualificationLevel:
      row.qualificationLevel != null && row.qualificationLevel !== ''
        ? String(row.qualificationLevel)
        : '',
    status: row.status ?? 0,
    address: row.address ?? '',
    remark: row.remark ?? '',
  })
  showForm.value = true
  success.value = ''
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
}

async function submitForm() {
  error.value = ''
  success.value = ''
  const payload = {
    supplierCode: form.supplierCode.trim(),
    supplierName: form.supplierName.trim(),
    contactPerson: form.contactPerson?.trim() || undefined,
    contactPhone: form.contactPhone?.trim() || undefined,
    email: form.email?.trim() || undefined,
    region: form.region?.trim() || undefined,
    mainCategory: form.mainCategory?.trim() || undefined,
    qualificationLevel:
      form.qualificationLevel === '' || form.qualificationLevel == null
        ? undefined
        : Number(form.qualificationLevel),
    status:
      form.status === '' || form.status == null ? undefined : Number(form.status),
    address: form.address?.trim() || undefined,
    remark: form.remark?.trim() || undefined,
  }
  try {
    if (isEdit.value) {
      await updateSupplier(editingId.value, payload)
      success.value = '已保存修改'
    } else {
      await createSupplier(payload)
      success.value = '已新增供应商'
    }
    showForm.value = false
    editingId.value = null
    search()
  } catch (e) {
    error.value = e.message || '保存失败'
  }
}

async function remove(row) {
  if (!confirm(`确定删除供应商「${row.supplierName}」？`)) return
  error.value = ''
  try {
    await deleteSupplier(row.id)
    success.value = '已删除'
    search()
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

function statusLabel(v) {
  return STATUS_MAP[v] ?? v ?? '—'
}

function levelLabel(v) {
  return LEVEL_MAP[v] ?? v ?? '—'
}

function supplierStatusBadgeClass(v) {
  const map = {
    0: 'badge--slate',
    1: 'badge--amber',
    2: 'badge--cyan',
    3: 'badge--emerald',
    4: 'badge--stone',
    5: 'badge--rose',
  }
  return map[v] ?? 'badge--slate'
}

function levelBadgeClass(v) {
  const map = { 1: 'badge--slate', 2: 'badge--sky', 3: 'badge--emerald' }
  return map[v] ?? 'badge--slate'
}

onMounted(load)

watch(showForm, (val) => {
  document.body.style.overflow = val ? 'hidden' : ''
})
</script>

<template>
  <div class="page portal-supplier">
    <div class="stats">
      <div class="stat">
        <b>{{ pageResult.total }}</b>
        <span>当前供应商数</span>
      </div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h1>供应商列表</h1>
          <p class="lead">管理供应商编码、联系人、资质与状态信息</p>
        </div>
        <div class="toolbar">
          <button type="button" class="btn" :disabled="loading" @click="load">
            刷新
          </button>
          <button type="button" class="btn secondary" @click="openCreate">
            新增供应商
          </button>
        </div>
      </div>

      <div v-if="error" class="msg error">{{ error }}</div>
      <div v-if="success" class="msg ok">{{ success }}</div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <div class="search-field">
          <label>供应商名称</label>
          <input v-model="query.supplierName" placeholder="模糊搜索" @keyup.enter="search" />
        </div>
        <div class="search-field">
          <label>状态</label>
          <select v-model="query.status">
            <option value="">全部</option>
            <option :value="0">注册</option>
            <option :value="1">待审核</option>
            <option :value="2">已准入</option>
            <option :value="3">合作中</option>
            <option :value="4">冻结</option>
            <option :value="5">黑名单</option>
          </select>
        </div>
        <button type="button" class="btn" @click="search">查询</button>
        <button type="button" class="btn secondary" @click="resetSearch">重置</button>
      </div>

      <!-- 弹窗遮罩 -->
      <Teleport to="body">
        <div v-if="showForm" class="modal-overlay" @click.self="cancelForm">
        <div class="modal-dialog">
          <div class="modal-header">
            <h2>{{ isEdit ? '编辑供应商' : '新增供应商' }}</h2>
            <button class="modal-close" @click="cancelForm" title="关闭">&times;</button>
          </div>
          <div class="modal-body">
            <div class="form-grid">
              <label class="field">
                供应商编码 *
                <input v-model="form.supplierCode" required />
              </label>
              <label class="field">
                供应商名称 *
                <input v-model="form.supplierName" required />
              </label>
              <label class="field">
                联系人
                <input v-model="form.contactPerson" />
              </label>
              <label class="field">
                联系电话
                <input v-model="form.contactPhone" />
              </label>
              <label class="field">
                邮箱
                <input v-model="form.email" type="email" />
              </label>
              <label class="field">
                所属地区
                <input v-model="form.region" />
              </label>
              <label class="field">
                主营品类
                <input v-model="form.mainCategory" />
              </label>
              <label class="field">
                资质等级
                <select v-model="form.qualificationLevel">
                  <option value="">未选</option>
                  <option value="1">初级</option>
                  <option value="2">中级</option>
                  <option value="3">高级</option>
                </select>
              </label>
              <label class="field">
                状态
                <select v-model.number="form.status">
                  <option :value="0">注册</option>
                  <option :value="1">待审核</option>
                  <option :value="2">已准入</option>
                  <option :value="3">合作中</option>
                  <option :value="4">冻结</option>
                  <option :value="5">黑名单</option>
                </select>
              </label>
              <label class="field" style="grid-column: 1 / -1">
                地址
                <input v-model="form.address" />
              </label>
              <label class="field" style="grid-column: 1 / -1">
                备注
                <textarea v-model="form.remark" />
              </label>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn" @click="submitForm">保存</button>
            <button type="button" class="btn secondary" @click="cancelForm">取消</button>
          </div>
        </div>
      </div>
      </Teleport>

      <div v-if="loading" class="loading-block">
        <span class="loading-spinner" aria-hidden="true" />
        正在拉取数据…
      </div>
      <div v-else-if="pageResult.records.length === 0" class="empty-state">
        <span class="emoji">📋</span>
        暂无供应商数据，点击「新增供应商」开始录入。
      </div>
      <div v-else class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>联系人</th>
              <th>电话</th>
              <th>资质</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pageResult.records" :key="row.id">
              <td class="mono">{{ row.supplierCode }}</td>
              <td><strong>{{ row.supplierName }}</strong></td>
              <td>{{ row.contactPerson || '—' }}</td>
              <td>{{ row.contactPhone || '—' }}</td>
              <td>
                <span
                  class="badge"
                  :class="levelBadgeClass(row.qualificationLevel)"
                >
                  {{ levelLabel(row.qualificationLevel) }}
                </span>
              </td>
              <td>
                <span class="badge" :class="supplierStatusBadgeClass(row.status)">
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td class="mono">{{ row.updateTime || row.createTime || '—' }}</td>
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

      <Pagination
        v-if="pageResult.total > 0"
        :current="query.pageNum"
        :total="pageResult.total"
        :page-size="query.pageSize"
        @update:page="onPageChange"
        @update:size="onPageSizeChange"
      />
    </section>
  </div>
</template>
