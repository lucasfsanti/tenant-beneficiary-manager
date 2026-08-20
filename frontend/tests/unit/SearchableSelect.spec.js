import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SearchableSelect from '../../src/components/SearchableSelect.vue'

const optionLabel = (option) => option.label

describe('SearchableSelect', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('invokes the injected search function and renders matches as options', async () => {
    const search = vi.fn(() =>
      Promise.resolve([
        { id: 'a1', label: 'Ana Silva' },
        { id: 'a2', label: 'Ana Souza' }
      ])
    )
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(search).toHaveBeenCalledWith('ana')
    const options = wrapper.findAll('.searchable-select__option')
    expect(options).toHaveLength(2)
    expect(options[0].text()).toBe('Ana Silva')
  })

  it('selecting an option commits v-model and shows its label as the field text', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    await wrapper.find('.searchable-select__option').trigger('mousedown')

    expect(wrapper.emitted('update:modelValue')).toEqual([[null], ['a1']])
    expect(wrapper.find('input').element.value).toBe('Ana Silva')
    expect(wrapper.find('.searchable-select__options').exists()).toBe(false)
  })

  it('shows the initialLabel on mount without calling search', () => {
    const search = vi.fn()
    const wrapper = mount(SearchableSelect, {
      props: { search, optionLabel, initialLabel: 'Pessoa Já Selecionada' }
    })

    expect(wrapper.find('input').element.value).toBe('Pessoa Já Selecionada')
    expect(search).not.toHaveBeenCalled()
  })

  it('typing after a selection clears the committed value until a new pick', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('.searchable-select__option').trigger('mousedown')

    await wrapper.find('input').setValue('Ana Silva Edited')

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([null])
  })

  it('shows a "no matches" state when the search returns nothing', async () => {
    const search = vi.fn(() => Promise.resolve([]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('zzz')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhum resultado encontrado.')
  })

  it('selects the highlighted option on Enter, and Escape closes the list', async () => {
    const search = vi.fn(() =>
      Promise.resolve([
        { id: 'a1', label: 'Ana Silva' },
        { id: 'a2', label: 'Ana Souza' }
      ])
    )
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    await wrapper.find('input').trigger('keydown.down')
    await wrapper.find('input').trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['a1'])

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('input').trigger('keydown.esc')

    expect(wrapper.find('.searchable-select__options').exists()).toBe(false)
  })

  it('closes the list when focus leaves the field', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.find('.searchable-select__options').exists()).toBe(true)

    await wrapper.find('.searchable-select').trigger('focusout')

    expect(wrapper.find('.searchable-select__options').exists()).toBe(false)
  })

  it('re-runs the search when the field regains focus with existing text', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('.searchable-select').trigger('focusout')
    expect(wrapper.find('.searchable-select__options').exists()).toBe(false)

    await wrapper.find('input').trigger('focus')
    await flushPromises()

    expect(search).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.searchable-select__options').exists()).toBe(true)
  })

  it('updates the shown text when initialLabel changes after mount', async () => {
    const search = vi.fn()
    const wrapper = mount(SearchableSelect, {
      props: { search, optionLabel, initialLabel: 'Pessoa A' }
    })

    await wrapper.setProps({ initialLabel: 'Pessoa B' })
    expect(wrapper.find('input').element.value).toBe('Pessoa B')

    await wrapper.setProps({ initialLabel: '' })
    expect(wrapper.find('input').element.value).toBe('')
  })

  it('selects the first option on Enter when nothing has been highlighted yet', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('input').trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['a1'])
  })

  it('ignores Enter/arrow keys when the options list is not open', async () => {
    const search = vi.fn()
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').trigger('keydown.down')
    await wrapper.find('input').trigger('keydown.enter')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('clearing the input closes the list without searching again', async () => {
    const search = vi.fn(() => Promise.resolve([{ id: 'a1', label: 'Ana Silva' }]))
    const wrapper = mount(SearchableSelect, { props: { search, optionLabel } })

    await wrapper.find('input').setValue('ana')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    await wrapper.find('input').setValue('')

    expect(wrapper.find('.searchable-select__options').exists()).toBe(false)
    expect(search).toHaveBeenCalledTimes(1)
  })
})
