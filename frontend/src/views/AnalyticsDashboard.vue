<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import EChart from '@/components/EChart.vue'
import {
  fetchConcentration,
  fetchSupplierScore,
  fetchBuyerProfile,
  fetchRelationRisk,
} from '@/api/graphApi'

// —— 集中度风险 ——
const concMaterial = ref('冷轧钢板')
const concentration = ref(null)
const concLoading = ref(false)
const concError = ref('')
const concSearched = ref(false)

// —— 供应商综合评分 ——
const scoreSupplierId = ref('')
const score = ref(null)
const scoreLoading = ref(false)
const scoreError = ref('')

// —— 采购员画像 ——
const profileUserId = ref('')
const profile = ref(null)
const profileLoading = ref(false)
const profileError = ref('')

// —— 关联风险 ——
const riskSupplierId = ref('')
const risk = ref([])
const riskLoading = ref(false)
const riskError = ref('')

async function loadConcentration() {
  if (!concMaterial.value.trim()) return
  concLoading.value = true
  concError.value = ''
  concSearched.value = true
  try {
    const list = await fetchConcentration(concMaterial.value.trim())
    concentration.value = Array.isArray(list) && list.length ? list[0] : null
  } catch (e) {
    concError.value = e.message || '加载失败'
    concentration.value = null
  } finally {
    concLoading.value = false
  }
}

async function loadScore() {
  if (!scoreSupplierId.value.trim()) return
  scoreLoading.value = true
  scoreError.value = ''
  try {
    score.value = await fetchSupplierScore(Number(scoreSupplierId.value.trim()))
  } catch (e) {
    scoreError.value = e.message || '加载失败'
    score.value = null
  } finally {
    scoreLoading.value = false
  }
}

async function loadProfile() {
  if (!profileUserId.value.trim()) return
  profileLoading.value = true
  profileError.value = ''
  try {
    profile.value = await fetchBuyerProfile(Number(profileUserId.value.trim()))
  } catch (e) {
    profileError.value = e.message || '加载失败'
    profile.value = null
  } finally {
    profileLoading.value = false
  }
}

async function loadRisk() {
  if (!riskSupplierId.value.trim()) return
  riskLoading.value = true
  riskError.value = ''
  try {
    risk.value = await fetchRelationRisk(Number(riskSupplierId.value.trim()), 3)
  } catch (e) {
    riskError.value = e.message || '加载失败'
    risk.value = []
  } finally {
    riskLoading.value = false
  }
}

// —— 图表配置 ——

function hhiColor(hhi) {
  if (hhi > 0.25) return '#d27486'
  if (hhi > 0.15) return '#c6a15f'
  return '#6fb58f'
}

const concentrationOption = computed(() => {
  if (!concentration.value) return {}
  const hhi = concentration.value.hhi
  return {
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 1,
        startAngle: 210,
        endAngle: -30,
        radius: '95%',
        axisLine: {
          lineStyle: {
            width: 14,
            color: [
              [0.15, '#6fb58f'],
              [0.25, '#c6a15f'],
              [1, '#d27486'],
            ],
          },
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          fontSize: 30,
          color: hhiColor(hhi),
          formatter: (v) => (v * 100).toFixed(1) + '%',
          offsetCenter: [0, '0%'],
        },
        data: [{ value: hhi, name: 'HHI' }],
      },
    ],
  }
})

const scoreOption = computed(() => {
  if (!score.value || !score.value.dimensions) return {}
  const dims = score.value.dimensions
  return {
    backgroundColor: 'transparent',
    radar: {
      indicator: dims.map((d) => ({ name: d.name, max: 100 })),
      radius: '62%',
      splitNumber: 4,
      axisName: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
      splitArea: {
        areaStyle: {
          color: ['rgba(122,162,209,0.02)', 'rgba(122,162,209,0.06)'],
        },
      },
      axisLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: dims.map((d) => d.score),
            name: score.value.supplierName || '供应商',
            areaStyle: { color: 'rgba(122,162,209,0.25)' },
            lineStyle: { color: '#7aa2d1', width: 2 },
            itemStyle: { color: '#7aa2d1' },
          },
        ],
      },
    ],
  }
})

const categoryOption = computed(() => {
  if (!profile.value || !profile.value.categoryCoverage) return {}
  return {
    backgroundColor: 'transparent',
    color: ['#7aa2d1', '#8b9bb0', '#6fb58f', '#c6a15f', '#9aa4af', '#d27486'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#94a3b8' } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '46%'],
        data: profile.value.categoryCoverage.map((c) => ({
          name: c.material,
          value: c.count,
        })),
        label: { color: '#94a3b8' },
        itemStyle: { borderColor: '#101318', borderWidth: 2 },
      },
    ],
  }
})

const bargainingOption = computed(() => {
  if (!profile.value || !profile.value.bargaining) return {}
  const b = profile.value.bargaining
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#94a3b8' } },
    grid: { left: 50, right: 20, top: 36, bottom: 40 },
    xAxis: {
      type: 'category',
      data: b.map((x) => x.materialName),
      axisLabel: { color: '#94a3b8', interval: 0 },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.15)' } },
    },
    series: [
      {
        name: '我的采购价',
        type: 'bar',
        data: b.map((x) => x.myPrice),
        itemStyle: { color: '#7aa2d1', borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '市场均价',
        type: 'bar',
        data: b.map((x) => x.marketPrice),
        itemStyle: { color: '#8b9bb0', borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})

onMounted(() => {
  loadConcentration()
})
</script>

<template>
  <div class="page analytics">
    <section class="panel">
      <div class="panel-head">
        <div>
          <h1>供应链智能分析</h1>
          <p class="lead">基于 Neo4j 知识图谱的集中度风险、供应商评分与采购画像</p>
        </div>
      </div>
    </section>

    <div class="grid">
      <!-- 集中度风险 -->
      <section class="panel card">
        <div class="panel-head">
          <div>
            <h1>供应链集中度风险</h1>
            <p class="lead">赫芬达尔指数（HHI）</p>
          </div>
        </div>
        <div class="search-bar">
          <div class="search-field">
            <label>物料名称</label>
            <input v-model="concMaterial" placeholder="如：冷轧钢板" @keyup.enter="loadConcentration" />
          </div>
          <button type="button" class="btn" :disabled="concLoading" @click="loadConcentration">
            分析
          </button>
        </div>
        <div v-if="concError" class="msg error">{{ concError }}</div>
        <div v-else-if="concentration" class="chart-block">
          <EChart :option="concentrationOption" height="220px" />
          <div class="chart-meta">
            <span class="stat-chip">供应商数：<b>{{ concentration.supplierCount }}</b></span>
            <span class="stat-chip">风险等级：<b>{{ concentration.riskLevel }}</b></span>
          </div>
        </div>
        <div v-else-if="concSearched" class="msg">
          未找到物料「{{ concMaterial }}」的供应数据。请确认物料名称，或先在 Neo4j 导入图谱种子数据（graph/seed.cypher）。
        </div>
        <div v-else class="empty-state">
          <span class="emoji">📊</span>
          输入物料名称后点击「分析」查看集中度风险。
        </div>
      </section>

      <!-- 供应商综合评分 -->
      <section class="panel card">
        <div class="panel-head">
          <div>
            <h1>供应商综合评分</h1>
            <p class="lead">价格 / 质量 / 交期 / 服务 / 风险</p>
          </div>
        </div>
        <div class="search-bar">
          <div class="search-field">
            <label>供应商ID</label>
            <input v-model="scoreSupplierId" placeholder="如：2（上海宝钢）" @keyup.enter="loadScore" />
          </div>
          <button type="button" class="btn" :disabled="scoreLoading" @click="loadScore">
            分析
          </button>
        </div>
        <div v-if="scoreError" class="msg error">{{ scoreError }}</div>
        <div v-else-if="score" class="chart-block">
          <div class="score-total">综合得分 <b>{{ score.totalScore }}</b></div>
          <EChart :option="scoreOption" height="240px" />
        </div>
        <div v-else class="empty-state">
          <span class="emoji">🎯</span>
          输入供应商ID后点击「分析」查看综合评分。
        </div>
      </section>

      <!-- 采购员画像 -->
      <section class="panel card wide">
        <div class="panel-head">
          <div>
            <h1>采购员画像</h1>
            <p class="lead">品类偏好、议价能力与供应商合作网络</p>
          </div>
        </div>
        <div class="search-bar">
          <div class="search-field">
            <label>用户ID</label>
            <input v-model="profileUserId" placeholder="如：1" @keyup.enter="loadProfile" />
          </div>
          <button type="button" class="btn" :disabled="profileLoading" @click="loadProfile">
            分析
          </button>
        </div>
        <div v-if="profileError" class="msg error">{{ profileError }}</div>
        <div v-else-if="profile" class="profile-grid">
          <div class="chart-block">
            <h2>品类覆盖</h2>
            <EChart :option="categoryOption" height="240px" />
          </div>
          <div class="chart-block">
            <h2>议价能力（我的价 vs 市场价）</h2>
            <EChart :option="bargainingOption" height="240px" />
          </div>
          <div class="chart-block">
            <h2>供应商合作网络</h2>
            <div v-if="profile.supplierNetwork && profile.supplierNetwork.length" class="table-wrap">
              <table class="data">
                <thead>
                  <tr>
                    <th>供应商</th>
                    <th>订单数</th>
                    <th>累计金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(s, i) in profile.supplierNetwork" :key="i">
                    <td><strong>{{ s.supplierName }}</strong></td>
                    <td class="mono">{{ s.orderCount }}</td>
                    <td class="mono">{{ s.totalAmount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state">暂无合作记录</div>
          </div>
        </div>
        <div v-else class="empty-state">
          <span class="emoji">🧑‍💼</span>
          输入用户ID后点击「分析」查看采购员画像。
        </div>
      </section>

      <!-- 关联风险穿透 -->
      <section class="panel card wide">
        <div class="panel-head">
          <div>
            <h1>关联风险穿透</h1>
            <p class="lead">股权 / 股东 / 高管多层关联</p>
          </div>
        </div>
        <div class="search-bar">
          <div class="search-field">
            <label>供应商ID</label>
            <input v-model="riskSupplierId" placeholder="如：2（上海宝钢）" @keyup.enter="loadRisk" />
          </div>
          <button type="button" class="btn" :disabled="riskLoading" @click="loadRisk">
            分析
          </button>
        </div>
        <div v-if="riskError" class="msg error">{{ riskError }}</div>
        <div v-else-if="risk.length" class="table-wrap">
          <table class="data">
            <thead>
              <tr>
                <th>关联供应商</th>
                <th>关联深度</th>
                <th>关系链</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in risk" :key="i">
                <td><strong>{{ r.relatedName }}</strong></td>
                <td class="mono">{{ r.depth }} 层</td>
                <td>{{ (r.relationChain || []).join(' → ') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state">
          <span class="emoji">🕸️</span>
          输入供应商ID后点击「分析」查看关联风险，或该供应商暂无关联企业。
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.card.wide {
  grid-column: 1 / -1;
}

.chart-block {
  margin-top: 0.5rem;
}

.chart-meta {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
  justify-content: center;
}

.stat-chip {
  padding: 0.35rem 0.8rem;
  border-radius: 999px;
  font-size: 0.82rem;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--card-border);
}

.stat-chip b {
  color: var(--text);
}

.score-total {
  text-align: center;
  font-size: 1rem;
  color: var(--muted);
  margin-bottom: 0.25rem;
}

.score-total b {
  font-size: 1.6rem;
  color: var(--text);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.profile-grid .chart-block h2 {
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  color: var(--muted);
}

@media (max-width: 800px) {
  .grid,
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
