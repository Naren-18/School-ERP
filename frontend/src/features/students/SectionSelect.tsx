import { Select, type SelectProps } from 'antd'
import { useSections } from '../settings/queries'

type Props = Omit<SelectProps<number>, 'options'> & {
  yearId: number | undefined
  /** Only sections of this class (e.g. detained students stay in their class). */
  classId?: number
}

/** Section picker for a year, labelled "Class 5 – A" and ordered by class. */
export function SectionSelect({ yearId, classId, ...rest }: Props) {
  const { data: sections = [], isLoading } = useSections(yearId)
  return (
    <Select<number>
      showSearch
      optionFilterProp="label"
      placeholder="Class – section"
      loading={isLoading}
      style={{ minWidth: 160 }}
      {...rest}
      options={sections
        .filter((s) => classId === undefined || s.classId === classId)
        .map((s) => ({ value: s.id, label: `${s.className} – ${s.name}` }))}
    />
  )
}
