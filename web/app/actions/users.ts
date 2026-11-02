'use server'

import { revalidatePath } from 'next/cache'
import { redirect } from 'next/navigation'
import type { DocumentReference } from 'firebase-admin/firestore'
import { HttpError, requireAdmin } from '@/lib/auth'
import { getAdminAuth, getAdminDb } from '@/lib/firebase-admin'
import { createUserSchema, resetPasswordSchema } from '@/lib/validation'

export type ActionState = {
  error: string | null
  success?: string | null
}

function errorMessage(error: unknown): string {
  if (error instanceof HttpError) return error.message
  if (error instanceof Error) return error.message
  return 'Ha ocurrido un error'
}

export async function createUserAction(
  _previous: ActionState,
  formData: FormData
): Promise<ActionState> {
  let uid: string
  try {
    await requireAdmin()
    const parsed = createUserSchema.safeParse({
      email: formData.get('email'),
      password: formData.get('password'),
      displayName: formData.get('displayName'),
      admin: formData.get('admin') === 'on'
    })
    if (!parsed.success) {
      return { error: parsed.error.issues[0]?.message ?? 'Datos no válidos' }
    }

    const auth = getAdminAuth()
    const user = await auth.createUser({
      email: parsed.data.email,
      password: parsed.data.password,
      displayName: parsed.data.displayName || undefined,
      emailVerified: true,
      disabled: false
    })
    if (parsed.data.admin) {
      await auth.setCustomUserClaims(user.uid, { admin: true })
    }
    await getAdminDb().collection('users').doc(user.uid).set({
      email: parsed.data.email,
      displayName: parsed.data.displayName || null,
      role: parsed.data.admin ? 'admin' : 'user',
      status: 'active',
      createdAt: Date.now(),
      lastSeenAt: null
    })
    uid = user.uid
  } catch (error) {
    return { error: errorMessage(error) }
  }

  revalidatePath('/users')
  redirect(`/users/${uid}`)
}

export async function toggleUserDisabledAction(
  _previous: ActionState,
  formData: FormData
): Promise<ActionState> {
  try {
    await requireAdmin()
    const uid = String(formData.get('uid') ?? '')
    const disabled = formData.get('disabled') === 'true'
    if (!uid) return { error: 'Usuario no válido' }

    await getAdminAuth().updateUser(uid, { disabled })
    if (disabled) {
      await getAdminAuth().revokeRefreshTokens(uid)
    }
    await getAdminDb()
      .collection('users')
      .doc(uid)
      .set({ status: disabled ? 'disabled' : 'active' }, { merge: true })
    revalidatePath('/users')
    revalidatePath(`/users/${uid}`)
    return {
      error: null,
      success: disabled ? 'Usuario desactivado' : 'Usuario activado'
    }
  } catch (error) {
    return { error: errorMessage(error) }
  }
}

export async function toggleAdminAction(
  _previous: ActionState,
  formData: FormData
): Promise<ActionState> {
  try {
    const admin = await requireAdmin()
    const uid = String(formData.get('uid') ?? '')
    const makeAdmin = formData.get('admin') === 'true'
    if (!uid) return { error: 'Usuario no válido' }
    if (uid === admin.uid) {
      return { error: 'No puedes cambiar tu propio rol de administrador' }
    }

    await getAdminAuth().setCustomUserClaims(uid, { admin: makeAdmin })
    await getAdminDb()
      .collection('users')
      .doc(uid)
      .set({ role: makeAdmin ? 'admin' : 'user' }, { merge: true })
    revalidatePath('/users')
    revalidatePath(`/users/${uid}`)
    return {
      error: null,
      success: makeAdmin
        ? 'Permisos de administrador concedidos'
        : 'Permisos de administrador retirados'
    }
  } catch (error) {
    return { error: errorMessage(error) }
  }
}

export async function resetPasswordAction(
  _previous: ActionState,
  formData: FormData
): Promise<ActionState> {
  try {
    await requireAdmin()
    const uid = String(formData.get('uid') ?? '')
    const parsed = resetPasswordSchema.safeParse({
      password: formData.get('password')
    })
    if (!uid) return { error: 'Usuario no válido' }
    if (!parsed.success) {
      return { error: parsed.error.issues[0]?.message ?? 'Contraseña no válida' }
    }
    await getAdminAuth().updateUser(uid, { password: parsed.data.password })
    await getAdminAuth().revokeRefreshTokens(uid)
    return { error: null, success: 'Contraseña actualizada' }
  } catch (error) {
    return { error: errorMessage(error) }
  }
}

export async function deleteUserAction(
  _previous: ActionState,
  formData: FormData
): Promise<ActionState> {
  try {
    const admin = await requireAdmin()
    const uid = String(formData.get('uid') ?? '')
    if (!uid) return { error: 'Usuario no válido' }
    if (uid === admin.uid) {
      return { error: 'No puedes borrar tu propia cuenta desde el panel' }
    }

    await getAdminAuth().deleteUser(uid)
    await deleteDocumentRecursive(
      getAdminDb().collection('users').doc(uid)
    )
    revalidatePath('/users')
  } catch (error) {
    return { error: errorMessage(error) }
  }

  redirect('/users')
}

async function deleteDocumentRecursive(reference: DocumentReference) {
  const collections = await reference.listCollections()
  for (const collection of collections) {
    const documents = await collection.listDocuments()
    for (const document of documents) {
      await deleteDocumentRecursive(document)
    }
  }
  await reference.delete()
}
