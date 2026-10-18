import { memo, useCallback, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Empty, Flex, Input, type InputRef, Select, Skeleton, Tag, Typography } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { examsApi, type MarkRow } from '../../api/exams'
import { PageHeader } from '../../components/PageHeader'

/** What the box shows: a number, "AB" for absent, or empty for not entered. */
const shown = (r: MarkRow) => (r.absent ? 'AB' : r.marks === null ? '' : String(r.marks))

type Parsed = { marks: number | null; absent: boolean } | { error: string }

function parse(text: string, max: number): Parsed {
  const t = text.trim().toUpperCase()
  if (t === '') return { marks: null, absent: false }
  if (t === 'A' || t === 'AB') return { marks: null, absent: true }
  const n = Number(t)
  if (!/^\d+(\.\d{1,2})?$/.test(t) || Number.isNaN(n)) return { error: 'Enter a number, or AB for absent' }
  if (n > max) return { error: `More than ${max}` }
  return { marks: n, absent: false }
}

/**
 * Marks for one paper in one section. Type marks and press Enter to move down;
 * "a" or "AB" marks absent; empty means not entered. Only changed rows are saved.
 */
export function MarksEntryPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  /** Unsaved edits, tagged with the sheet they belong to; another sheet starts clean. */
  const [edits, setEdits] = useState<{ sheet: string; values: Record<number, string> }>({ sheet: '', values: {} })
  const inputs = useRef<Record<number, InputRef | null>>({})

  const exams = useQuery({ queryKey: ['exams', undefined], queryFn: () => examsApi.list() })
  const examId = Number(params.get('exam')) || exams.data?.find((e) => !e.published)?.id || exams.data?.[0]?.id
  const options = useQuery({
    queryKey: ['entry-options', examId],
    queryFn: () => examsApi.entryOptions(examId!),
    enabled: !!examId,
  })
  const optionKey = params.get('paper') && params.get('section') ? `${params.get('paper')}-${params.get('section')}` : undefined
  const option =
    options.data?.find((o) => `${o.examSubjectId}-${o.sectionId}` === optionKey) ??
    options.data?.find((o) => o.entered < o.students) ??
    options.data?.[0]
  const sheet = useQuery({
    queryKey: ['mark-sheet', option?.examSubjectId, option?.sectionId],
    queryFn: () => examsApi.sheet(option!.examSubjectId, option!.sectionId),
    enabled: !!option,
  })

  const sheetKey = option ? `${option.examSubjectId}-${option.sectionId}` : ''
  const drafts = useMemo(() => (edits.sheet === sheetKey ? edits.values : {}), [edits, sheetKey])

  const data = sheet.data
  const rows = useMemo(() => data?.rows ?? [], [data])
  const changed = rows.filter((r) => drafts[r.enrollmentId] !== undefined && drafts[r.enrollmentId].trim().toUpperCase() !== shown(r))
  const errors = data ? changed.filter((r) => 'error' in parse(drafts[r.enrollmentId], data.maxMarks)) : []

  const save = useMutation({
    mutationFn: () =>
      examsApi.saveMarks(
        option!.examSubjectId,
        option!.sectionId,
        changed.map((r) => {
          const p = parse(drafts[r.enrollmentId], data!.maxMarks) as { marks: number | null; absent: boolean }
          return { enrollmentId: r.enrollmentId, marks: p.marks, absent: p.absent, remarks: r.remarks }
        }),
      ),
    onSuccess: (saved) => {
      queryClient.setQueryData(['mark-sheet', option?.examSubjectId, option?.sectionId], saved)
      queryClient.invalidateQueries({ queryKey: ['entry-options', examId] })
      setEdits({ sheet: sheetKey, values: {} })
      message.success(`Saved marks for ${changed.length} student${changed.length === 1 ? '' : 's'}`)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const setDraft = useCallback(
    (id: number, v: string) =>
      setEdits((e) => ({ sheet: sheetKey, values: { ...(e.sheet === sheetKey ? e.values : {}), [id]: v } })),
    [sheetKey],
  )
  const focusNext = useCallback(
    (id: number) => {
      const i = rows.findIndex((r) => r.enrollmentId === id)
      const next = rows[i + 1]
      if (next) inputs.current[next.enrollmentId]?.focus({ cursor: 'all' })
    },
    [rows],
  )
  const confirmLeave = () => changed.length === 0 || window.confirm('You have unsaved marks. Discard them?')
  const pick = (next: Record<string, string>) => {
    if (!confirmLeave()) return
    setParams(next, { replace: true })
  }

  const entered = rows.filter((r) => {
    const d = drafts[r.enrollmentId]
    return d !== undefined ? d.trim() !== '' : r.absent || r.marks !== null
  }).length

  return (
    <>
      <PageHeader
        title="Enter marks"
        extra={
          <>
            <Select
              style={{ minWidth: 120 }}
              value={examId}
              loading={exams.isLoading}
              placeholder="Exam"
              onChange={(id) => pick({ exam: String(id) })}
              options={(exams.data ?? []).map((e) => ({
                value: e.id,
                label: (
                  <>
                    {e.name} {e.published && <Tag color="green">Published</Tag>}
                  </>
                ),
              }))}
            />
            <Select
              style={{ minWidth: 240 }}
              value={option ? `${option.examSubjectId}-${option.sectionId}` : undefined}
              loading={options.isLoading}
              placeholder="Class – section · subject"
              showSearch
              optionFilterProp="search"
              onChange={(key: string) => {
                const [paper, section] = key.split('-')
                pick({ exam: String(examId), paper, section })
              }}
              options={(options.data ?? []).map((o) => ({
                value: `${o.examSubjectId}-${o.sectionId}`,
                search: `${o.className} ${o.sectionName} ${o.subjectName}`,
                label: (
                  <Flex justify="space-between" gap={8}>
                    <span>
                      {o.className} – {o.sectionName} · {o.subjectName}
                    </span>
                    <Typography.Text type={o.entered >= o.students && o.students > 0 ? 'success' : 'secondary'}>
                      {o.entered}/{o.students}
                    </Typography.Text>
                  </Flex>
                ),
              }))}
            />
          </>
        }
      />

      {exams.data?.length === 0 ? (
        <Card>
          <Empty description="No exams this year yet. They are set up under Exams → Exam setup." />
        </Card>
      ) : options.data?.length === 0 ? (
        <Card>
          <Empty description="Nothing to enter for this exam. Teachers see the subjects assigned to them, once the exam has papers for that class." />
        </Card>
      ) : sheet.isLoading || !data ? (
        <Skeleton active />
      ) : (
        <>
          {data.lockedReason && <Alert type="info" showIcon style={{ marginBottom: 12 }} title={data.lockedReason} />}
          <Card size="small" styles={{ body: { padding: 0 } }}>
            <Flex justify="space-between" wrap gap={8} style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0' }}>
              <Typography.Text>
                <b>{data.subjectName}</b> · {data.sectionLabel} · Max {data.maxMarks}
                {data.passMarks !== null && ` · Pass ${data.passMarks}`}
              </Typography.Text>
              <Typography.Text type="secondary">
                {entered} of {rows.length} entered · type AB for absent
              </Typography.Text>
            </Flex>
            {rows.length === 0 && <Empty style={{ padding: 24 }} description="No students in this section" />}
            {rows.map((r) => (
              <MarkInputRow
                key={r.enrollmentId}
                row={r}
                value={drafts[r.enrollmentId] ?? shown(r)}
                max={data.maxMarks}
                pass={data.passMarks}
                editable={data.editable}
                onChange={setDraft}
                onEnter={focusNext}
                inputRef={(el) => {
                  inputs.current[r.enrollmentId] = el
                }}
              />
            ))}
          </Card>
          {data.editable && (
            <div style={{ position: 'sticky', bottom: 0, padding: '12px 0', background: 'var(--ant-color-bg-layout)', zIndex: 5 }}>
              <Button
                type="primary"
                size="large"
                block
                icon={<CheckOutlined />}
                loading={save.isPending}
                disabled={changed.length === 0 || errors.length > 0}
                onClick={() => save.mutate()}
              >
                {errors.length > 0
                  ? `Fix ${errors.length} entr${errors.length === 1 ? 'y' : 'ies'} first`
                  : changed.length > 0
                    ? `Save marks (${changed.length} changed)`
                    : 'No changes'}
              </Button>
            </div>
          )}
        </>
      )}
    </>
  )
}

interface RowProps {
  row: MarkRow
  value: string
  max: number
  pass: number | null
  editable: boolean
  onChange: (enrollmentId: number, value: string) => void
  onEnter: (enrollmentId: number) => void
  inputRef: (el: InputRef | null) => void
}

const MarkInputRow = memo(function MarkInputRow({ row, value, max, pass, editable, onChange, onEnter, inputRef }: RowProps) {
  const p = parse(value, max)
  const error = 'error' in p ? p.error : null
  const absent = !error && 'absent' in p && p.absent
  const below = !error && 'marks' in p && p.marks !== null && pass !== null && p.marks < pass
  return (
    <Flex align="center" gap={8} style={{ padding: '6px 12px', borderBottom: '1px solid #f0f0f0', background: absent ? '#fff7e6' : undefined }}>
      <Typography.Text type="secondary" style={{ width: 28, textAlign: 'right', flex: 'none' }}>
        {row.rollNo ?? '–'}
      </Typography.Text>
      <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
        {row.fullName}
      </Typography.Text>
      <Flex vertical align="flex-end" style={{ flex: 'none' }}>
        <Input
          ref={inputRef}
          value={value}
          disabled={!editable}
          inputMode="decimal"
          enterKeyHint="next"
          autoComplete="off"
          aria-label={`Marks for ${row.fullName}`}
          status={error ? 'error' : below ? 'warning' : undefined}
          style={{ width: 84, textAlign: 'right', fontWeight: 600 }}
          suffix={<Typography.Text type="secondary" style={{ fontSize: 11 }}>/{max}</Typography.Text>}
          onChange={(e) => onChange(row.enrollmentId, e.target.value)}
          onFocus={(e) => e.target.select()}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === 'ArrowDown') {
              e.preventDefault()
              onEnter(row.enrollmentId)
            }
          }}
        />
        {error && (
          <Typography.Text type="danger" style={{ fontSize: 11 }}>
            {error}
          </Typography.Text>
        )}
      </Flex>
    </Flex>
  )
})
