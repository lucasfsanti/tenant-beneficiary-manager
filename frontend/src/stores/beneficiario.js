import { defineStore } from 'pinia'
import beneficiarioApi from '../services/beneficiarioApi'
import { extractProblem } from '../services/problemDetail'

export const useBeneficiarioStore = defineStore('beneficiario', {
  state: () => ({
    items: [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    loading: false,
    problem: null,
    lastFilters: {}
  }),
  actions: {
    async fetchList({ pessoaNome, status, page = 0, size = 20 } = {}) {
      this.loading = true
      this.problem = null
      this.lastFilters = { pessoaNome, status, page, size }
      try {
        const response = await beneficiarioApi.list({ pessoaNome, status, page, size })
        this.items = response.data.content
        this.page = response.data.page
        this.totalPages = response.data.totalPages
        this.totalElements = response.data.totalElements
      } catch (err) {
        this.problem = extractProblem(err)
      } finally {
        this.loading = false
      }
    },
    async refetch() {
      await this.fetchList(this.lastFilters)
    },
    clear() {
      this.items = []
      this.page = 0
      this.totalPages = 0
      this.totalElements = 0
    },
    async create(payload) {
      this.problem = null
      try {
        await beneficiarioApi.create(payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async update(id, payload) {
      this.problem = null
      try {
        await beneficiarioApi.update(id, payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async remove(id) {
      this.problem = null
      try {
        await beneficiarioApi.remove(id)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    }
  }
})
