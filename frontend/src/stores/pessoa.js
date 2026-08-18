import { defineStore } from 'pinia'
import pessoaApi from '../services/pessoaApi'
import { extractProblem } from '../services/problemDetail'

export const usePessoaStore = defineStore('pessoa', {
  state: () => ({
    items: [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    loading: false,
    problem: null
  }),
  actions: {
    async fetchList({ nome, page = 0, size = 20 } = {}) {
      this.loading = true
      this.problem = null
      try {
        const response = await pessoaApi.list({ nome, page, size })
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
    async create(payload) {
      this.problem = null
      try {
        await pessoaApi.create(payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async update(id, payload) {
      this.problem = null
      try {
        await pessoaApi.update(id, payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async remove(id) {
      this.problem = null
      try {
        await pessoaApi.remove(id)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    }
  }
})
