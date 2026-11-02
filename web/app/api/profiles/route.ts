import { NextResponse } from 'next/server'
import { apiError } from '@/lib/api'
import { requireBearerUser } from '@/lib/auth'
import { encryptSecret } from '@/lib/crypto'
import { getAdminDb } from '@/lib/firebase-admin'
import { profileSchema } from '@/lib/validation'

export const runtime = 'nodejs'

export async function POST(request: Request) {
  try {
    const user = await requireBearerUser(request)
    const data = profileSchema.parse(await request.json())
    const now = Date.now()
    const ref = getAdminDb()
      .collection('users')
      .doc(user.uid)
      .collection('iptvProfiles')
      .doc()

    await ref.set({
      name: data.name,
      kind: data.kind,
      m3uUrlEnc:
        data.kind === 'm3u' && data.m3uUrl
          ? encryptSecret(data.m3uUrl)
          : null,
      xtreamHost: data.kind === 'xtream' ? data.xtreamHost || null : null,
      xtreamUsername:
        data.kind === 'xtream' ? data.xtreamUsername || null : null,
      xtreamPasswordEnc:
        data.kind === 'xtream' && data.xtreamPassword
          ? encryptSecret(data.xtreamPassword)
          : null,
      epgUrlEnc: data.epgUrl ? encryptSecret(data.epgUrl) : null,
      createdAt: now,
      updatedAt: now,
      createdBy: user.uid
    })

    return NextResponse.json({ id: ref.id }, { status: 201 })
  } catch (error) {
    return apiError(error)
  }
}
