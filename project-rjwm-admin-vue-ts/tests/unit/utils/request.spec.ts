import axios from 'axios'
jest.mock('@/store/modules/user', () => ({ UserModule: { token: 'test-token' } }))
jest.mock('@/router', () => ({ push: jest.fn() }))
jest.mock('element-ui', () => ({ Message: jest.fn(), MessageBox: jest.fn() }))
import service from '@/utils/request'
import { pending } from '@/utils/requestOptimize'

describe('duplicate request cancellation', () => {
  afterEach(() => Object.keys(pending).forEach(key => delete pending[key]))

  it('preserves the first in-flight request and releases it on completion', async () => {
    let release: any
    service.defaults.adapter = config => new Promise(resolve => {
      release = () => resolve({ data: { code: 1 }, status: 200, statusText: 'OK', headers: {}, config })
    })
    const first = service.get('/duplicate')
    await new Promise(resolve => setTimeout(resolve, 0))
    const error = await service.get('/duplicate').catch(error => error)
    expect(axios.isCancel(error)).toBe(true)
    expect(Object.keys(pending)).toHaveLength(1)
    release()
    await first
    expect(Object.keys(pending)).toHaveLength(0)
  })
})
