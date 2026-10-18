import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, DatePicker, Flex, Skeleton, Tooltip, Typography } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { attendanceApi } from '../../api/attendance'
import { NON_WORKING_BG, STATUS, formatPercent } from './statusStyle'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** A month calendar of one student's attendance (student profile → Attendance tab). */
export function StudentAttendanceTab({ studentId }: { studentId: number }) {
  const [month, setMonth] = useState(dayjs().startOf('month'))
  const key = month.format('YYYY-MM')
  const { data, isLoading } = useQuery({
    queryKey: ['attendance-student', studentId, key],
    queryFn: () => attendanceApi.studentMonth(studentId, key),
  })

  // Monday-first grid: blank cells before the 1st.
  const leading = (month.day() + 6) % 7

  return (
    <Flex vertical gap={16} style={{ maxWidth: 560 }}>
      <Flex align="center" gap={8} wrap>
        <Button icon={<LeftOutlined />} onClick={() => setMonth(month.subtract(1, 'month'))} aria-label="Previous month" />
        <DatePicker picker="month" allowClear={false} format="MMMM YYYY" value={month} onChange={(d) => d && setMonth(d.startOf('month'))} />
        <Button
          icon={<RightOutlined />}
          disabled={!month.isBefore(dayjs().startOf('month'))}
          onClick={() => setMonth(month.add(1, 'month'))}
          aria-label="Next month"
        />
        {data && (
          <Typography.Text style={{ marginLeft: 'auto' }}>
            <b>{formatPercent(data.counts.percentage)}</b> · {data.counts.present} P · {data.counts.absent} A · {data.counts.leave} L
            {data.counts.halfDay > 0 && ` · ${data.counts.halfDay} H`}
          </Typography.Text>
        )}
      </Flex>
      {isLoading || !data ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 4 }}>
          {WEEKDAYS.map((w) => (
            <Typography.Text key={w} type="secondary" style={{ textAlign: 'center', fontSize: 12 }}>
              {w}
            </Typography.Text>
          ))}
          {Array.from({ length: leading }, (_, i) => (
            <div key={`blank-${i}`} />
          ))}
          {data.days.map((d, i) => {
            const s = data.statuses[i]
            const bg = s ? STATUS[s].bg : d.nonWorkingReason ? NON_WORKING_BG : undefined
            const tip = s ? STATUS[s].label : (d.nonWorkingReason ?? (d.future ? '' : 'Not marked'))
            return (
              <Tooltip key={d.date} title={tip}>
                <div
                  style={{
                    aspectRatio: '1',
                    borderRadius: 8,
                    border: '1px solid #f0f0f0',
                    background: bg,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    opacity: d.future ? 0.4 : 1,
                  }}
                >
                  <span style={{ fontSize: 12 }}>{dayjs(d.date).date()}</span>
                  {s && <b style={{ color: STATUS[s].color }}>{s}</b>}
                </div>
              </Tooltip>
            )
          })}
        </div>
      )}
    </Flex>
  )
}
