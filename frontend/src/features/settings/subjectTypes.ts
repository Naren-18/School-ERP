import type { SubjectType } from '../../api/academic'

export const SUBJECT_TYPES: { value: SubjectType; label: string; color: string }[] = [
  { value: 'CORE', label: 'Core', color: 'blue' },
  { value: 'LANGUAGE', label: 'Language', color: 'purple' },
  { value: 'ELECTIVE', label: 'Elective', color: 'cyan' },
  { value: 'CO_CURRICULAR', label: 'Co-curricular', color: 'orange' },
]
