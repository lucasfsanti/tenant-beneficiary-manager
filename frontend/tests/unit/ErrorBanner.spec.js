import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorBanner from '../../src/components/ErrorBanner.vue'

describe('ErrorBanner', () => {
  it('renders nothing when there is no problem', () => {
    const wrapper = mount(ErrorBanner, { props: { problem: null } })

    expect(wrapper.find('.error-banner').exists()).toBe(false)
  })

  it('renders a fallback title when the problem has none', () => {
    const wrapper = mount(ErrorBanner, { props: { problem: {} } })

    expect(wrapper.find('.error-banner__title').text()).toBe('Erro')
    expect(wrapper.find('.error-banner__detail').exists()).toBe(false)
    expect(wrapper.find('.error-banner__list').exists()).toBe(false)
  })

  it('renders the title and detail when both are present', () => {
    const wrapper = mount(ErrorBanner, {
      props: { problem: { title: 'Falha de validação', detail: 'Campos inválidos.' } }
    })

    expect(wrapper.find('.error-banner__title').text()).toBe('Falha de validação')
    expect(wrapper.find('.error-banner__detail').text()).toBe('Campos inválidos.')
  })

  it('renders each field error in the list', () => {
    const wrapper = mount(ErrorBanner, {
      props: {
        problem: {
          title: 'Falha de validação',
          errors: [
            { field: 'cpf', message: 'CPF inválido' },
            { field: 'nome', message: 'obrigatório' }
          ]
        }
      }
    })

    const items = wrapper.findAll('.error-banner__list li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('cpf')
    expect(items[0].text()).toContain('CPF inválido')
    expect(items[1].text()).toContain('nome')
  })
})
