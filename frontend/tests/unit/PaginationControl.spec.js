import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PaginationControl from '../../src/components/PaginationControl.vue'

describe('PaginationControl', () => {
  it('renders nothing when there is only one page', () => {
    const wrapper = mount(PaginationControl, { props: { page: 0, totalPages: 1 } })

    expect(wrapper.find('.pagination-control').exists()).toBe(false)
  })

  it('disables "Anterior" on the first page and "Próxima" on the last', () => {
    const wrapper = mount(PaginationControl, { props: { page: 0, totalPages: 3 } })

    const buttons = wrapper.findAll('button')
    expect(buttons[0].attributes('disabled')).toBeDefined()
    expect(buttons[1].attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('Página 1 de 3')
  })

  it('disables "Próxima" on the last page', () => {
    const wrapper = mount(PaginationControl, { props: { page: 2, totalPages: 3 } })

    const buttons = wrapper.findAll('button')
    expect(buttons[0].attributes('disabled')).toBeUndefined()
    expect(buttons[1].attributes('disabled')).toBeDefined()
  })

  it('emits change with the previous/next page index', async () => {
    const wrapper = mount(PaginationControl, { props: { page: 1, totalPages: 3 } })

    await wrapper.findAll('button')[0].trigger('click')
    await wrapper.findAll('button')[1].trigger('click')

    expect(wrapper.emitted('change')).toEqual([[0], [2]])
  })
})
