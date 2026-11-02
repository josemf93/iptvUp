import { NextResponse } from 'next/server'
import { apiError } from '@/lib/api'
import { requireBearerUser } from '@/lib/auth'
import { encryptSecret } from '@/lib/crypto'
import { getAdminDb } from '@/lib/firebase-admin'
import { profileSchema } from '@/lib/validation'

export const runtime = 'nodejs'

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const user = await requireBearerUser(request)
    const { id } = await params
    const data = profileSchema.parse(await request.json())
    const ref = getAdminDb()
      .collection('users')
      .doc(user.uid)
      .collection('iptvProfiles')
      .doc(id)
    const existing = await ref.get()
    if (!existing.exists) {
      return NextResponse.json(
        { error: 'Perfil no encontrado' },
        { status: 404 }
      )
    }

    await ref.set(
      {
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
        updatedAt: Date.now()
      },
      { merge: true }
    )

    return NextResponse.json({ ok: true })
  } catch (error) {
    return apiError(error)
  }
}

export async function DELETE(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const user = await requireBearerUser(request)
    const { id } = await params
    await getAdminDb()
      .collection('users')
      .doc(user.uid)
      .collection('iptvProfiles')
      .doc(id)
      .delete()
    return NextResponse.json({ ok: true })
  } catch (error) {
    return apiError(error)
  }
}
