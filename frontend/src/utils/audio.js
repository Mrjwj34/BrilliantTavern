export function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onloadend = () => {
      const result = reader.result
      if (typeof result === 'string') {
        const commaIndex = result.indexOf(',')
        resolve(commaIndex >= 0 ? result.substring(commaIndex + 1) : result)
      } else {
        reject(new Error('无法读取音频数据'))
      }
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

export function base64ToUint8Array(base64) {
  const binaryString = window.atob(base64)
  const len = binaryString.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i += 1) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes
}

export function uint8ArrayToBlob(uint8Array, mimeType = 'audio/webm') {
  return new Blob([uint8Array.buffer.slice(uint8Array.byteOffset, uint8Array.byteOffset + uint8Array.byteLength)], { type: mimeType })
}
