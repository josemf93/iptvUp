import { NextResponse } from 'next/server'
import { apiError } from '@/lib/api'
import { requireBearerUser } from '@/lib/auth'
import { decryptSecret } from '@/lib/crypto'
import { getAdminDb } from '@/lib/firebase-admin'

export const runtime = 'nodejs'

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const user = await requireBearerUser(request)
    const { id } = await params
    const doc = await getAdminDb()
      .collection('users')
      .doc(user.uid)
      .collection('iptvProfiles')
      .doc(id)
      .get()

    if (!doc.exists) {
      return NextResponse.json(
        { error: 'Perfil no encontrado' },
        { status: 404 }
      )
    }

    const m3uUrlEnc = doc.get('m3uUrlEnc') as string | null
    const xtreamPasswordEnc = doc.get('xtreamPasswordEnc') as string | null
    const epgUrlEnc = doc.get('epgUrlEnc') as string | null

    return NextResponse.json({
      m3uUrl: m3uUrlEnc ? decryptSecret(m3uUrlEnc) : null,
      xtreamHost: doc.get('xtreamHost') ?? null,
      xtreamUsername: doc.get('xtreamUsername') ?? null,
      xtreamPassword: xtreamPasswordEnc
        ? decryptSecret(xtreamPasswordEnc)
        : null,
      epgUrl: epgUrlEnc ? decryptSecret(epgUrlEnc) : null
    })
  } catch (error) {
    return apiError(error)
  }
}
