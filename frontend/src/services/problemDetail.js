export function extractProblem(error) {
  if (error?.response?.data && typeof error.response.data === 'object') {
    return error.response.data
  }
  return { title: 'Erro', detail: 'Ocorreu um erro inesperado. Tente novamente.' }
}
