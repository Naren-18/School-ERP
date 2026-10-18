import { useQuery } from '@tanstack/react-query'
import { Empty, Flex, Skeleton, Table, Tag, Typography } from 'antd'
import { errorMessage } from '../../api/client'
import { examsApi } from '../../api/exams'

const passTag = (passed: boolean | null) =>
  passed === null ? <Tag>Incomplete</Tag> : passed ? <Tag color="green">Pass</Tag> : <Tag color="red">Fail</Tag>

/** Student profile → Marks: published exams of the current year and the weighted final. */
export function StudentMarksTab({ studentId }: { studentId: number }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['student-results', studentId],
    queryFn: () => examsApi.studentResults(studentId),
  })
  if (isLoading) return <Skeleton active />
  if (isError) return <Typography.Text type="danger">{errorMessage(error)}</Typography.Text>
  if (!data || data.exams.length === 0) {
    return <Empty description="No published exam results this year yet." />
  }

  const subjects = Object.entries(data.subjectNames)
  const rows = subjects.map(([id, name]) => ({ id, name }))
  const weighted = data.exams.some((e) => e.weightage !== null)

  return (
    <Flex vertical gap={12}>
      <Typography.Text type="secondary">{data.academicYearName}</Typography.Text>
      <Table
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 'max-content' }}
        dataSource={rows}
        columns={[
          { title: 'Subject', dataIndex: 'name', fixed: 'left' },
          ...data.exams.map((e) => ({
            title: `${e.examName}${e.weightage ? ` (${e.weightage}%)` : ''}`,
            key: `e${e.examId}`,
            align: 'center' as const,
            render: (_: unknown, r: { id: string }) => {
              const s = e.result.subjects.find((x) => String(x.subjectId) === r.id)
              if (!s || !s.entered) return '–'
              if (s.absent) return <Typography.Text type="danger">AB</Typography.Text>
              return (
                <span style={{ color: s.passed === false ? '#cf1322' : undefined }}>
                  {s.marks}/{s.maxMarks} <Typography.Text type="secondary">{s.grade}</Typography.Text>
                </span>
              )
            },
          })),
          ...(weighted
            ? [
                {
                  title: 'Final',
                  key: 'final',
                  align: 'center' as const,
                  render: (_: unknown, r: { id: string }) => <b>{data.finalResult.subjectGrade[r.id] ?? '–'}</b>,
                },
              ]
            : []),
        ]}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <b>Overall</b>
            </Table.Summary.Cell>
            {data.exams.map((e, i) => (
              <Table.Summary.Cell key={e.examId} index={i + 1} align="center">
                {e.result.percent ?? '–'}% {e.result.grade} {passTag(e.result.passed)}
              </Table.Summary.Cell>
            ))}
            {weighted && (
              <Table.Summary.Cell index={data.exams.length + 1} align="center">
                <b>{data.finalResult.percent ?? '–'}%</b> {data.finalResult.grade} {passTag(data.finalResult.passed)}
              </Table.Summary.Cell>
            )}
          </Table.Summary.Row>
        )}
      />
    </Flex>
  )
}
