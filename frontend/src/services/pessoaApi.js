import api from './api'

export default {
  list(params) {
    return api.get('/pessoas', { params })
  },
  get(id) {
    return api.get(`/pessoas/${id}`)
  },
  create(payload) {
    return api.post('/pessoas', payload)
  },
  update(id, payload) {
    return api.put(`/pessoas/${id}`, payload)
  },
  remove(id) {
    return api.delete(`/pessoas/${id}`)
  }
}
