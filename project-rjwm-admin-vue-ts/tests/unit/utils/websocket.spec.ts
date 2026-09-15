import { buildSocketUrl } from '@/utils/websocket'

describe('WebSocket deployment URL', () => {
  it('uses secure same-origin WebSocket for an HTTPS production page', () => {
    expect(buildSocketUrl('', 'https://shop.example/order', 'client')).toBe('wss://shop.example/ws/client')
  })
  it('uses the development proxy without dropping the port', () => {
    expect(buildSocketUrl('/ws/', 'http://localhost:8081/', 'client')).toBe('ws://localhost:8081/ws/client')
  })
  it('retains an explicitly configured endpoint', () => {
    expect(buildSocketUrl('wss://api.example/ws/', 'https://shop.example', 'client')).toBe('wss://api.example/ws/client')
  })
})
