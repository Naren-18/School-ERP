import { useEffect } from 'react'
import { Select, Tag } from 'antd'
import { useYears } from './queries'

interface Props {
  value: number | undefined
  onChange: (yearId: number) => void
}

/** Academic year picker that selects the current year by default. */
export function YearSelect({ value, onChange }: Props) {
  const { data: years = [], isLoading } = useYears()

  useEffect(() => {
    if (value === undefined && years.length > 0) {
      onChange((years.find((y) => y.current) ?? years[0]).id)
    }
  }, [value, years, onChange])

  return (
    <Select
      style={{ minWidth: 160 }}
      loading={isLoading}
      value={value}
      onChange={onChange}
      placeholder="Academic year"
      options={years.map((y) => ({
        value: y.id,
        label: (
          <>
            {y.name} {y.current && <Tag color="green">Current</Tag>}
          </>
        ),
      }))}
    />
  )
}
