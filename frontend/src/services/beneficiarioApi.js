import api from './api'

export default {
  list(params) {
    return api.get('/beneficiarios', { params })
  },
  get(id) {
    return api.get(`/beneficiarios/${id}`)
  },
  create(payload) {
    return api.post('/beneficiarios', payload)
  },
  update(id, payload) {
    return api.put(`/beneficiarios/${id}`, payload)
  },
  remove(id) {
    return api.delete(`/beneficiarios/${id}`)
  }
}
