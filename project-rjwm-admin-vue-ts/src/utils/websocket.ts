export function buildSocketUrl(configured: string | undefined, pageUrl: string, clientId: string): string {
  const endpoint = new URL(configured || '/ws/', pageUrl)
  if (!['http:', 'https:', 'ws:', 'wss:'].includes(endpoint.protocol)) {
    throw new Error('Invalid WebSocket URL')
  }
  endpoint.protocol = endpoint.protocol === 'https:' || endpoint.protocol === 'wss:' ? 'wss:' : 'ws:'
  endpoint.pathname = endpoint.pathname.replace(/\/?$/, '/') + encodeURIComponent(clientId)
  return endpoint.toString()
}
