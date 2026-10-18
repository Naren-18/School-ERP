import { Tag } from 'antd'
import type { SubjectType } from '../../api/academic'
import { SUBJECT_TYPES } from './subjectTypes'

export function SubjectTypeTag({ type }: { type: SubjectType }) {
  const t = SUBJECT_TYPES.find((x) => x.value === type)
  return <Tag color={t?.color}>{t?.label ?? type}</Tag>
}
