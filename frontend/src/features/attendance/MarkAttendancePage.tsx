import { memo, useCallback, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, DatePicker, Empty, Flex, Select, Skeleton, Typography } from 'antd'
import { CheckCircleFilled, CheckOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { attendanceApi, type AttendanceStatus, type SheetStudent } from '../../api/attendance'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { PRINCIPAL_ADMIN } from '../../layout/menu'
import { API_DATE_FORMAT, DATE_FORMAT, formatDateTime } from '../../utils/format'
import { STATUS } from './statusStyle'

const CHOICES: AttendanceStatus[] = ['P', 'A', 'L']

/**
 * Mobile-first marking: everyone starts Present, tap A or L for the few who are
 * not, then Save. Big touch targets; one request to load, one to save.
 */
export function MarkAttendancePage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { hasAnyRole } = useAuth()
  const canPickDate = hasAnyRole(PRINCIPAL_ADMIN)
  const [params, setParams] = useSearchParams()
  // ?date= opens a specific day (principal/admin); teachers always work on today.
  const requested = params.get('date')
  const [date, setDate] = useState(
    canPickDate && requested && dayjs(requested).isValid() ? requested : dayjs().format(API_DATE_FORMAT),
  )
  /** Taps since the sheet loaded; anything untouched shows its saved mark, or P. */
  const [changes, setChanges] = useState<Record<number, AttendanceStatus>>({})

  const sections = useQuery({ queryKey: ['attendance-sections', date], queryFn: () => attendanceApi.sections(date) })
  const sectionId = Number(params.get('section')) || sections.data?.[0]?.sectionId
  const sheet = useQuery({
    queryKey: ['attendance-sheet', sectionId, date],
    queryFn: () => attendanceApi.sheet(sectionId!, date),
    enabled: !!sectionId,
  })

  const current = useCallback(
    (s: SheetStudent): AttendanceStatus => changes[s.enrollmentId] ?? s.status ?? 'P',
    [changes],
  )
  const students = useMemo(() => sheet.data?.students ?? [], [sheet.data])
  const tally = useMemo(() => {
    const t: Record<AttendanceStatus, number> = { P: 0, A: 0, L: 0, H: 0 }
    students.forEach((s) => t[current(s)]++)
    return t
  }, [students, current])

  const save = useMutation({
    mutationFn: () =>
      attendanceApi.save(sectionId!, date, students.map((s) => ({ enrollmentId: s.enrollmentId, status: current(s) }))),
    onSuccess: (saved) => {
      queryClient.setQueryData(['attendance-sheet', sectionId, date], saved)
      queryClient.invalidateQueries({ queryKey: ['attendance-sections'] })
      queryClient.invalidateQueries({ queryKey: ['attendance-today'] })
      setChanges({})
      message.success(`Saved: ${saved.counts.present} present, ${saved.counts.absent} absent`)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const setStatus = useCallback((enrollmentId: number, status: AttendanceStatus) => {
    setChanges((c) => ({ ...c, [enrollmentId]: status }))
  }, [])
  const pickSection = (id: number) => {
    setParams({ section: String(id), date }, { replace: true })
    setChanges({})
  }

  const data = sheet.data
  const editable = !!data?.editable
  const dirty = Object.keys(changes).length > 0 || (data && !data.marked && students.length > 0)

  return (
    <>
      <PageHeader
        title="Mark attendance"
        subtitle={canPickDate ? undefined : `Today, ${dayjs().format(DATE_FORMAT)}`}
        extra={
          <>
            <Select
              style={{ minWidth: 170 }}
              value={sectionId}
              onChange={pickSection}
              loading={sections.isLoading}
              placeholder="Section"
              options={(sections.data ?? []).map((s) => ({
                value: s.sectionId,
                label: (
                  <span>
                    {s.className} – {s.sectionName} {s.marked && <CheckCircleFilled style={{ color: STATUS.P.color }} />}
                  </span>
                ),
              }))}
            />
            {canPickDate && (
              <DatePicker
                allowClear={false}
                format={DATE_FORMAT}
                value={dayjs(date)}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                onChange={(d) => {
                  if (!d) return
                  setDate(d.format(API_DATE_FORMAT))
                  setChanges({})
                }}
              />
            )}
          </>
        }
      />

      {sections.data && sections.data.length === 0 ? (
        <Card>
          <Empty description="No sections to mark. Teachers see the sections they class-teach or are assigned to." />
        </Card>
      ) : sheet.isLoading || !data ? (
        <Skeleton active />
      ) : (
        <>
          {data.blockedReason ? (
            <Alert type={data.marked ? 'info' : 'warning'} showIcon style={{ marginBottom: 12 }} title={data.blockedReason} />
          ) : (
            data.marked && (
              <Alert
                type="success"
                showIcon
                style={{ marginBottom: 12 }}
                title={`Marked by ${data.markedByName ?? 'staff'} · ${formatDateTime(data.markedAt)}. You can still change it.`}
              />
            )
          )}

          <Card size="small" styles={{ body: { padding: 0 } }}>
            <Flex justify="space-between" align="center" wrap gap={8} style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0' }}>
              <Typography.Text>
                <b style={{ color: STATUS.P.color }}>{tally.P} P</b> · <b style={{ color: STATUS.A.color }}>{tally.A} A</b> ·{' '}
                <b style={{ color: STATUS.L.color }}>{tally.L} L</b>
                {tally.H > 0 && <> · <b style={{ color: STATUS.H.color }}>{tally.H} H</b></>} of {students.length}
              </Typography.Text>
              {editable && (
                <Button
                  size="small"
                  onClick={() => setChanges(Object.fromEntries(students.map((s) => [s.enrollmentId, 'P'])))}
                >
                  All present
                </Button>
              )}
            </Flex>
            {students.map((s) => (
              <StudentRow key={s.enrollmentId} student={s} status={current(s)} editable={editable} onChange={setStatus} />
            ))}
          </Card>

          {editable && (
            <div style={{ position: 'sticky', bottom: 0, padding: '12px 0', background: 'var(--ant-color-bg-layout)', zIndex: 5 }}>
              <Button
                type="primary"
                size="large"
                block
                icon={<CheckOutlined />}
                loading={save.isPending}
                disabled={!dirty}
                onClick={() => save.mutate()}
              >
                {data.marked ? 'Save changes' : `Save attendance (${students.length})`}
              </Button>
            </div>
          )}
        </>
      )}
    </>
  )
}

interface RowProps {
  student: SheetStudent
  status: AttendanceStatus
  editable: boolean
  onChange: (enrollmentId: number, status: AttendanceStatus) => void
}

/** One student; memoised so a tap re-renders only that row. */
const StudentRow = memo(function StudentRow({ student, status, editable, onChange }: RowProps) {
  const choices = status === 'H' ? [...CHOICES, 'H' as const] : CHOICES
  return (
    <Flex
      align="center"
      gap={8}
      style={{
        padding: '8px 12px',
        borderBottom: '1px solid #f0f0f0',
        background: status === 'P' ? undefined : STATUS[status].bg,
      }}
    >
      <Typography.Text type="secondary" style={{ width: 28, textAlign: 'right', flexShrink: 0 }}>
        {student.rollNo ?? '–'}
      </Typography.Text>
      <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
        {student.fullName}
      </Typography.Text>
      <Flex gap={6} style={{ flexShrink: 0 }}>
        {choices.map((c) => {
          const active = status === c
          return (
            <button
              key={c}
              type="button"
              disabled={!editable}
              aria-pressed={active}
              aria-label={`${STATUS[c].label}: ${student.fullName}`}
              onClick={() => onChange(student.enrollmentId, c)}
              style={{
                width: 44,
                height: 44,
                borderRadius: 10,
                fontWeight: 700,
                fontSize: 16,
                cursor: editable ? 'pointer' : 'default',
                border: `2px solid ${active ? STATUS[c].color : '#d9d9d9'}`,
                background: active ? STATUS[c].color : '#fff',
                color: active ? '#fff' : STATUS[c].color,
                opacity: !editable && !active ? 0.4 : 1,
              }}
            >
              {c}
            </button>
          )
        })}
      </Flex>
    </Flex>
  )
})
