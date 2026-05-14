import axios from 'axios'

const API_BASE = process.env.VUE_APP_API_BASE || ''
const API_KEY = process.env.VUE_APP_API_KEY || ''
// SSE 请求直连后端，避免代理缓冲导致流式传输失效
const SSE_BASE = process.env.VUE_APP_SSE_BASE || 'http://localhost:8080'

const client = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
})

// Add API key to requests if configured
client.interceptors.request.use(config => {
  if (API_KEY) {
    config.headers['X-API-Key'] = API_KEY
  }
  return config
})

/**
 * Parse uploaded file to text
 */
export async function parseFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await client.post('/api/parse', formData)
  return response.data
}

/**
 * Stream proofread results via SSE (fetch-based for POST + JSON body)
 * 直连后端，不走代理，确保 SSE 流式传输正常
 */
export async function proofread(text, signal) {
  const headers = {
    'Content-Type': 'application/json',
  }
  if (API_KEY) {
    headers['X-API-Key'] = API_KEY
  }

  const url = `${SSE_BASE}/api/proofread`

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ text }),
    signal,
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => null)
    throw new Error(errorData?.error || `请求失败: ${response.status}`)
  }

  return response
}
