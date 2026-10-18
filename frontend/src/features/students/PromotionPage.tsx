import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Checkbox, Col, Flex, Form, Popconfirm, Result, Row, Steps, Table, Typography } from 'antd'
import { errorMessage } from '../../api/client'
import { studentsApi, type SectionStudent } from '../../api/students'
import { PageHeader } from '../../components/PageHeader'
import { useSections, useYears } from '../settings/queries'
import { YearSelect } from '../settings/YearSelect'
import { SectionSelect } from './SectionSelect'

/**
 * Year-end promotion, one section at a time: pick the source section, the
 * next-year section, then untick anyone who is detained.
 */
export function PromotionPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { data: years = [] } = useYears()
  const [fromYear, setFromYear] = useState<number>()
  const [sourceId, setSourceId] = useState<number>()
  const [toYearChoice, setToYear] = useState<number>()
  const [targetId, setTargetId] = useState<number>()
  const [detainId, setDetainId] = useState<number>()
  const [unticked, setUnticked] = useState<Set<number>>(new Set())
  const [result, setResult] = useState<{ promoted: number; detained: number; placedInDetainSection: number }>()

  const source = useSections(fromYear).data?.find((s) => s.id === sourceId)
  const fromStart = years.find((y) => y.id === fromYear)?.startDate
  const laterYears = years.filter((y) => fromStart && y.startDate > fromStart).sort((a, b) => a.startDate.localeCompare(b.startDate))
  // Default target year: the one right after the source year.
  const toYear = toYearChoice ?? laterYears[0]?.id

  const students = useQuery({
    queryKey: ['section-students', sourceId],
    queryFn: () => studentsApi.sectionStudents(sourceId!),
    enabled: !!sourceId,
  })
  const list = students.data ?? []
  const promoteIds = list.filter((s) => !unticked.has(s.enrollmentId)).map((s) => s.enrollmentId)
  const detainedCount = list.length - promoteIds.length

  const promote = useMutation({
    mutationFn: () =>
      studentsApi.promote({
        sourceSectionId: sourceId!,
        targetSectionId: targetId!,
        promoteEnrollmentIds: promoteIds,
        detainSectionId: detainedCount > 0 ? detainId : undefined,
      }),
    onSuccess: (r) => {
      setResult(r)
      queryClient.invalidateQueries({ queryKey: ['students'] })
      queryClient.invalidateQueries({ queryKey: ['section-students'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const restart = () => {
    setSourceId(undefined)
    setTargetId(undefined)
    setDetainId(undefined)
    setUnticked(new Set())
    setResult(undefined)
  }

  if (result) {
    return (
      <Result
        status="success"
        title={`${result.promoted} promoted, ${result.detained} detained`}
        subTitle={
          result.detained > 0 && result.placedInDetainSection === 0
            ? 'Detained students are not in any section next year yet. Enrol them from their profile.'
            : undefined
        }
        extra={<Button type="primary" onClick={restart}>Promote another section</Button>}
      />
    )
  }

  const step = !sourceId ? 0 : !targetId ? 1 : 2
  const toggle = (s: SectionStudent, checked: boolean) =>
    setUnticked((cur) => {
      const next = new Set(cur)
      if (checked) next.delete(s.enrollmentId)
      else next.add(s.enrollmentId)
      return next
    })

  return (
    <>
      <PageHeader title="Promote students" subtitle="Moves a section's students into next year's sections." />
      <Steps current={step} size="small" style={{ marginBottom: 16 }} items={[{ title: 'From' }, { title: 'To' }, { title: 'Students' }]} />
      {laterYears.length === 0 && fromYear && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          title="Create next academic year and its sections first (Setup → Academic years, Classes & sections)."
        />
      )}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card title="1. From" size="small" style={{ marginBottom: 16 }}>
            <Form layout="vertical">
              <Form.Item label="Academic year">
                <YearSelect
                  value={fromYear}
                  onChange={(y) => {
                    setFromYear(y)
                    restart()
                  }}
                />
              </Form.Item>
              <Form.Item label="Section" style={{ marginBottom: 0 }}>
                <SectionSelect
                  yearId={fromYear}
                  value={sourceId}
                  style={{ width: '100%' }}
                  onChange={(v) => {
                    setSourceId(v)
                    setUnticked(new Set())
                  }}
                />
              </Form.Item>
            </Form>
          </Card>
          <Card title="2. To" size="small">
            <Form layout="vertical">
              <Form.Item label="Next academic year">
                <SelectYear years={laterYears} value={toYear} onChange={(y) => {
                  setToYear(y)
                  setTargetId(undefined)
                  setDetainId(undefined)
                }} />
              </Form.Item>
              <Form.Item label="Promote into">
                <SectionSelect yearId={toYear} value={targetId} onChange={setTargetId} disabled={!sourceId} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="Detained students go to" extra="Optional. Same class, next year." style={{ marginBottom: 0 }}>
                <SectionSelect
                  yearId={toYear}
                  classId={source?.classId}
                  allowClear
                  value={detainId}
                  onChange={setDetainId}
                  disabled={!sourceId}
                  style={{ width: '100%' }}
                  placeholder="Decide later"
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={16}>
          <Card
            title="3. Students"
            size="small"
            extra={
              list.length > 0 && (
                <Typography.Text>
                  <b>{promoteIds.length}</b> promote · <b>{detainedCount}</b> detain
                </Typography.Text>
              )
            }
          >
            <Table<SectionStudent>
              rowKey="enrollmentId"
              size="small"
              loading={students.isLoading}
              dataSource={list}
              pagination={false}
              scroll={{ x: true }}
              locale={{ emptyText: sourceId ? 'No active students in this section' : 'Choose a section' }}
              columns={[
                {
                  title: (
                    <Checkbox
                      checked={list.length > 0 && unticked.size === 0}
                      indeterminate={unticked.size > 0 && unticked.size < list.length}
                      onChange={(e) => setUnticked(e.target.checked ? new Set() : new Set(list.map((s) => s.enrollmentId)))}
                    />
                  ),
                  key: 'pick',
                  width: 48,
                  render: (_, s) => <Checkbox checked={!unticked.has(s.enrollmentId)} onChange={(e) => toggle(s, e.target.checked)} />,
                },
                { title: 'Roll', dataIndex: 'rollNo', width: 60 },
                { title: 'Name', dataIndex: 'fullName' },
                { title: 'Adm. no.', dataIndex: 'admissionNo' },
                {
                  title: 'Result',
                  key: 'result',
                  render: (_, s) =>
                    unticked.has(s.enrollmentId) ? (
                      <Typography.Text type="warning">Detain</Typography.Text>
                    ) : (
                      <Typography.Text type="success">Promote</Typography.Text>
                    ),
                },
              ]}
            />
            <Flex justify="flex-end" style={{ marginTop: 16 }}>
              <Popconfirm
                title={`Promote ${promoteIds.length} and detain ${detainedCount}?`}
                description="This closes their places in the current section."
                onConfirm={() => promote.mutate()}
              >
                <Button type="primary" disabled={!sourceId || !targetId || list.length === 0} loading={promote.isPending}>
                  Promote
                </Button>
              </Popconfirm>
            </Flex>
          </Card>
        </Col>
      </Row>
    </>
  )
}

function SelectYear({ years, value, onChange }: { years: { id: number; name: string }[]; value?: number; onChange: (id: number) => void }) {
  return (
    <Flex gap={8} wrap>
      {years.length === 0 ? (
        <Typography.Text type="secondary">No later year yet</Typography.Text>
      ) : (
        years.map((y) => (
          <Button key={y.id} type={y.id === value ? 'primary' : 'default'} onClick={() => onChange(y.id)}>
            {y.name}
          </Button>
        ))
      )}
    </Flex>
  )
}
