import type { EnrollmentStatus, Gender, Relation, SocialCategory, StudentStatus } from '../../api/students'

export const GENDERS: { value: Gender; label: string }[] = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
]

export const RELATIONS: { value: Relation; label: string }[] = [
  { value: 'FATHER', label: 'Father' },
  { value: 'MOTHER', label: 'Mother' },
  { value: 'GUARDIAN', label: 'Guardian' },
]

export const CATEGORIES: { value: SocialCategory; label: string }[] = [
  { value: 'GEN', label: 'General' },
  { value: 'OBC', label: 'OBC' },
  { value: 'BC', label: 'BC' },
  { value: 'SC', label: 'SC' },
  { value: 'ST', label: 'ST' },
  { value: 'OTHER', label: 'Other' },
]

export const STUDENT_STATUS: Record<StudentStatus, { label: string; color: string }> = {
  ACTIVE: { label: 'Active', color: 'green' },
  LEFT: { label: 'Left', color: 'default' },
  TC_ISSUED: { label: 'TC issued', color: 'orange' },
  PASSED_OUT: { label: 'Passed out', color: 'blue' },
}

export const ENROLLMENT_STATUS: Record<EnrollmentStatus, { label: string; color: string }> = {
  ACTIVE: { label: 'Active', color: 'green' },
  PROMOTED: { label: 'Promoted', color: 'blue' },
  DETAINED: { label: 'Detained', color: 'orange' },
  LEFT: { label: 'Left', color: 'default' },
}

export const labelOf = <T extends string>(list: { value: T; label: string }[], value: T | null | undefined) =>
  list.find((x) => x.value === value)?.label ?? '—'
