import 'server-only'
import crypto from 'node:crypto'

function getKey(): Buffer {
  const raw = process.env.CREDENTIALS_KEY
  if (!raw) {
    throw new Error('Falta CREDENTIALS_KEY')
  }
  const key = Buffer.from(raw, 'base64')
  if (key.length !== 32) {
    throw new Error('CREDENTIALS_KEY debe tener 32 bytes en base64')
  }
  return key
}

export function encryptSecret(plain: string): string {
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', getKey(), iv)
  const encrypted = Buffer.concat([
    cipher.update(plain, 'utf8'),
    cipher.final()
  ])
  const tag = cipher.getAuthTag()
  return Buffer.concat([iv, tag, encrypted]).toString('base64')
}

export function decryptSecret(payload: string): string {
  const buffer = Buffer.from(payload, 'base64')
  if (buffer.length < 29) {
    throw new Error('Payload cifrado inválido')
  }
  const iv = buffer.subarray(0, 12)
  const tag = buffer.subarray(12, 28)
  const data = buffer.subarray(28)
  const decipher = crypto.createDecipheriv('aes-256-gcm', getKey(), iv)
  decipher.setAuthTag(tag)
  return Buffer.concat([decipher.update(data), decipher.final()]).toString('utf8')
}
