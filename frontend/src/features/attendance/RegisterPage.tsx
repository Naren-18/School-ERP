import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Empty, Select, Table, Tooltip } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { attendanceApi, type RegisterRow } from '../../api/attendance'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { saveBlob } from '../../utils/download'
import { NON_WORKING_BG, STATUS, formatPercent } from './statusStyle'

/** Students × days for one month, with totals; exportable as CSV. */
export function RegisterPage() {
  const { message } = App.useApp()
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'))
  const [chosen, setChosen] = useState<number>()
  const sections = useQuery({ queryKey: ['attendance-sections', 'register'], queryFn: () => attendanceApi.sections() })
  const sectionId = chosen ?? sections.data?.[0]?.sectionId

  const register = useQuery({
    queryKey: ['attendance-register', sectionId, month],
    queryFn: () => attendanceApi.register(sectionId!, month),
    enabled: !!sectionId,
  })
  const exportCsv = useMutation({
    mutationFn: () => attendanceApi.registerCsv(sectionId!, month),
    onSuccess: (blob) => saveBlob(blob, `attendance-${register.data?.sectionLabel ?? 'section'}-${month}.csv`),
    onError: (e) => message.error(errorMessage(e)),
  })

  const days = register.data?.days ?? []

  return (
    <>
      <PageHeader
        title="Monthly register"
        extra={
          <>
            <Select
              style={{ minWidth: 160 }}
              value={sectionId}
              onChange={setChosen}
              placeholder="Section"
              options={(sections.data ?? []).map((s) => ({ value: s.sectionId, label: `${s.className} – ${s.sectionName}` }))}
            />
            <DatePicker picker="month" allowClear={false} format="MMM YYYY" value={dayjs(month)} onChange={(d) => d && setMonth(d.format('YYYY-MM'))} />
            <Button icon={<DownloadOutlined />} disabled={!register.data} loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
              Export CSV
            </Button>
          </>
        }
      />
      <Card styles={{ body: { padding: 8 } }}>
        {sections.data?.length === 0 ? (
          <Empty description="No sections to show." />
        ) : (
          <Table<RegisterRow>
            rowKey="enrollmentId"
            size="small"
            bordered
            loading={register.isLoading}
            dataSource={register.data?.rows ?? []}
            pagination={false}
            scroll={{ x: 'max-content' }}
            columns={[
              { title: 'Roll', dataIndex: 'rollNo', width: 48, fixed: 'left' },
              { title: 'Name', dataIndex: 'fullName', width: 160, fixed: 'left', ellipsis: true },
              ...days.map((d, i) => ({
                title: (
                  <Tooltip title={d.nonWorkingReason ?? d.dayOfWeek}>
                    <div style={{ textAlign: 'center', lineHeight: 1.1 }}>
                      {dayjs(d.date).date()}
                      <br />
                      <span style={{ fontSize: 10, fontWeight: 400 }}>{d.dayOfWeek[0]}</span>
                    </div>
                  </Tooltip>
                ),
                key: d.date,
                width: 34,
                align: 'center' as const,
                onCell: () => ({ style: { padding: 2, background: d.nonWorkingReason ? NON_WORKING_BG : undefined } }),
                render: (_: unknown, row: RegisterRow) => {
                  const s = row.statuses[i]
                  return s ? <b style={{ color: STATUS[s].color }}>{s}</b> : null
                },
              })),
              { title: 'P', key: 'p', width: 40, align: 'center', render: (_, r) => r.counts.present + r.counts.halfDay / 2 },
              { title: 'A', key: 'a', width: 40, align: 'center', render: (_, r) => r.counts.absent },
              { title: 'L', key: 'l', width: 40, align: 'center', render: (_, r) => r.counts.leave },
              { title: '%', key: 'pct', width: 60, align: 'center', fixed: 'right', render: (_, r) => formatPercent(r.counts.percentage) },
            ]}
          />
        )}
      </Card>
    </>
  )
}
