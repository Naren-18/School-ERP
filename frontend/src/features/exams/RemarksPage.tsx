import { memo, useCallback, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Empty, Flex, Input, Select, Skeleton, Tag, Typography } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { examsApi, reportCardApi, resultSections, type CoScholasticArea, type NoteRow } from '../../api/exams'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { PRINCIPAL_ADMIN } from '../../layout/menu'

const GRADES = ['A', 'B', 'C']

interface Edit {
  remark: string | null
  grades: Record<string, string>
}

/**
 * Class teacher's remarks and co-scholastic grades for an exam, printed on the
 * report card. One card per student, so it works on a phone.
 */
export function RemarksPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { hasAnyRole, me } = useAuth()
  const [params, setParams] = useSearchParams()
  const exams = useQuery({ queryKey: ['exams', undefined], queryFn: () => examsApi.list() })
  const sectionsQuery = useQuery({ queryKey: ['result-sections'], queryFn: () => resultSections() })
  // Teachers write remarks only for the sections they class-teach.
  const sections = (sectionsQuery.data ?? []).filter((s) => hasAnyRole(PRINCIPAL_ADMIN) || s.classTeacherId === me?.user.id)
  const examId = Number(params.get('exam')) || exams.data?.find((e) => !e.published)?.id || exams.data?.[0]?.id
  const sectionId = Number(params.get('section')) || sections[0]?.id
  const sheetKey = `${examId}-${sectionId}`
  const sheet = useQuery({
    queryKey: ['notes', examId, sectionId],
    queryFn: () => reportCardApi.notes(examId!, sectionId!),
    enabled: !!examId && !!sectionId,
  })
  const [edits, setEdits] = useState<{ sheet: string; values: Record<number, Edit> }>({ sheet: '', values: {} })
  const changes = useMemo(() => (edits.sheet === sheetKey ? edits.values : {}), [edits, sheetKey])

  const save = useMutation({
    mutationFn: () =>
      reportCardApi.saveNotes(
        examId!,
        sectionId!,
        Object.entries(changes).map(([id, e]) => ({ enrollmentId: Number(id), remark: e.remark, grades: e.grades })),
      ),
    onSuccess: (saved) => {
      queryClient.setQueryData(['notes', examId, sectionId], saved)
      setEdits({ sheet: sheetKey, values: {} })
      message.success('Saved')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const change = useCallback(
    (row: NoteRow, edit: Partial<Edit>) =>
      setEdits((prev) => {
        const values = prev.sheet === sheetKey ? prev.values : {}
        const current = values[row.enrollmentId] ?? { remark: row.remark, grades: row.grades }
        return { sheet: sheetKey, values: { ...values, [row.enrollmentId]: { ...current, ...edit } } }
      }),
    [sheetKey],
  )
  const pick = (next: Record<string, string>) => {
    if (Object.keys(changes).length > 0 && !window.confirm('You have unsaved remarks. Discard them?')) return
    setParams(next, { replace: true })
  }

  const data = sheet.data
  const count = Object.keys(changes).length

  return (
    <>
      <PageHeader
        title="Remarks & grades"
        subtitle="Class teacher's remark and co-scholastic grades, printed on the report card."
        extra={
          <>
            <Select
              style={{ minWidth: 120 }}
              value={examId}
              placeholder="Exam"
              loading={exams.isLoading}
              onChange={(id) => pick({ exam: String(id), section: String(sectionId ?? '') })}
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
              style={{ minWidth: 150 }}
              value={sectionId}
              placeholder="Section"
              loading={sectionsQuery.isLoading}
              onChange={(id) => pick({ exam: String(examId ?? ''), section: String(id) })}
              options={sections.map((s) => ({ value: s.id, label: `${s.className} – ${s.name}` }))}
            />
          </>
        }
      />
      {sectionsQuery.data && sections.length === 0 ? (
        <Card>
          <Empty description="Remarks are written by the class teacher. You are not class teacher of any section this year." />
        </Card>
      ) : exams.data?.length === 0 ? (
        <Card>
          <Empty description="No exams this year yet." />
        </Card>
      ) : sheet.isError ? (
        <Alert type="error" title={errorMessage(sheet.error)} />
      ) : sheet.isLoading || !data ? (
        <Skeleton active />
      ) : (
        <>
          {data.lockedReason && <Alert type="info" showIcon style={{ marginBottom: 12 }} title={data.lockedReason} />}
          {data.areas.length === 0 && (
            <Typography.Paragraph type="secondary">
              No co-scholastic areas are set up (Exam setup → Co-scholastic), so only remarks are asked for.
            </Typography.Paragraph>
          )}
          <Flex vertical gap={8}>
            {data.rows.length === 0 && <Empty description="No students in this section" />}
            {data.rows.map((r) => (
              <StudentNote
                key={r.enrollmentId}
                row={r}
                edit={changes[r.enrollmentId]}
                areas={data.areas}
                editable={data.editable}
                onChange={change}
              />
            ))}
          </Flex>
          {data.editable && (
            <div style={{ position: 'sticky', bottom: 0, padding: '12px 0', background: 'var(--ant-color-bg-layout)', zIndex: 5 }}>
              <Button type="primary" size="large" block icon={<CheckOutlined />} disabled={count === 0} loading={save.isPending} onClick={() => save.mutate()}>
                {count > 0 ? `Save (${count} changed)` : 'No changes'}
              </Button>
            </div>
          )}
        </>
      )}
    </>
  )
}

interface NoteProps {
  row: NoteRow
  edit: Edit | undefined
  areas: CoScholasticArea[]
  editable: boolean
  onChange: (row: NoteRow, edit: Partial<Edit>) => void
}

const StudentNote = memo(function StudentNote({ row, edit, areas, editable, onChange }: NoteProps) {
  const remark = edit ? edit.remark : row.remark
  const grades = edit ? edit.grades : row.grades
  return (
    <Card size="small" styles={{ body: { padding: 10 } }}>
      <Flex vertical gap={8}>
        <Typography.Text strong>
          <Typography.Text type="secondary">{row.rollNo ?? '–'}.</Typography.Text> {row.fullName}
        </Typography.Text>
        {areas.length > 0 && (
          <Flex gap={8} wrap>
            {areas.map((a) => (
              <Flex key={a.id} align="center" gap={6}>
                <Typography.Text style={{ fontSize: 13 }}>{a.name}</Typography.Text>
                <Select
                  size="small"
                  style={{ width: 64 }}
                  disabled={!editable}
                  allowClear
                  value={grades[a.id] ?? undefined}
                  placeholder="–"
                  aria-label={`${a.name} grade for ${row.fullName}`}
                  onChange={(g: string | undefined) => {
                    const next = { ...grades }
                    if (g) next[a.id] = g
                    else delete next[a.id]
                    onChange(row, { grades: next })
                  }}
                  options={GRADES.map((g) => ({ value: g, label: g }))}
                />
              </Flex>
            ))}
          </Flex>
        )}
        <Input.TextArea
          value={remark ?? ''}
          disabled={!editable}
          maxLength={300}
          autoSize={{ minRows: 1, maxRows: 4 }}
          placeholder="Remark, e.g. Attentive in class; can read more at home."
          aria-label={`Remark for ${row.fullName}`}
          onChange={(e) => onChange(row, { remark: e.target.value || null })}
        />
      </Flex>
    </Card>
  )
})
