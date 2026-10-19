import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { App, Button, Card, Empty, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { DownloadOutlined, FilePdfOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { examsApi, resultSections, type ResultSheet, type SubjectResult } from '../../api/exams'
import { PageHeader } from '../../components/PageHeader'
import { downloadCsv } from '../../utils/csv'
import { openPdf } from '../../utils/openPdf'

type Row = ResultSheet['rows'][number]

const markText = (s: SubjectResult | undefined) => (!s || !s.entered ? '–' : s.absent ? 'AB' : String(s.marks))

/** Students × subjects for one exam in one section, with totals, grades and ranks; report cards. */
export function ResultsPage() {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const [printing, setPrinting] = useState<string | null>(null)
  const exams = useQuery({ queryKey: ['exams', undefined], queryFn: () => examsApi.list() })
  const sections = useQuery({ queryKey: ['result-sections'], queryFn: () => resultSections() })
  const examId = Number(params.get('exam')) || exams.data?.[0]?.id
  const sectionId = Number(params.get('section')) || sections.data?.[0]?.id
  const exam = exams.data?.find((e) => e.id === examId)
  const sheet = useQuery({
    queryKey: ['results', examId, sectionId],
    queryFn: () => examsApi.results(examId!, sectionId!),
    enabled: !!examId && !!sectionId,
  })
  const data = sheet.data
  // A class without papers in this exam (e.g. pre-primary): nothing to show or print.
  const noPapers = !!data && data.subjects.length === 0

  const print = async (key: string, p: { examId?: number; studentId?: number }) => {
    setPrinting(key)
    try {
      await openPdf(() => examsApi.reportCards({ sectionId: sectionId!, ...p }))
    } catch (e) {
      message.error(errorMessage(e))
    } finally {
      setPrinting(null)
    }
  }

  const subjectOf = (r: Row, subjectId: number) => r.result.subjects.find((s) => s.subjectId === subjectId)

  const exportCsv = () => {
    if (!data) return
    downloadCsv(
      `results-${data.examName}-${data.sectionLabel}.csv`.replace(/\s+/g, ''),
      ['Roll', 'Student', ...data.subjects.map((s) => `${s.subjectName} (${s.maxMarks})`), 'Total', 'Max', '%', 'Grade', 'Result', 'Rank'],
      data.rows.map((r) => [
        r.rollNo,
        r.fullName,
        ...data.subjects.map((s) => markText(subjectOf(r, s.subjectId))),
        r.result.total,
        r.result.maxTotal,
        r.result.percent,
        r.result.grade,
        r.result.passed === null ? 'Incomplete' : r.result.passed ? 'Pass' : 'Fail',
        r.rank,
      ]),
    )
  }

  return (
    <>
      <PageHeader
        title="Results"
        extra={
          <>
            <Select
              style={{ minWidth: 120 }}
              value={examId}
              loading={exams.isLoading}
              placeholder="Exam"
              onChange={(id) => setParams({ exam: String(id), section: String(sectionId ?? '') }, { replace: true })}
              options={(exams.data ?? []).map((e) => ({
                value: e.id,
                label: (
                  <>
                    {e.name} {e.published ? <Tag color="green">Published</Tag> : <Tag>Draft</Tag>}
                  </>
                ),
              }))}
            />
            <Select
              style={{ minWidth: 150 }}
              value={sectionId}
              loading={sections.isLoading}
              placeholder="Section"
              showSearch
              optionFilterProp="label"
              onChange={(id) => setParams({ exam: String(examId ?? ''), section: String(id) }, { replace: true })}
              options={(sections.data ?? []).map((s) => ({ value: s.id, label: `${s.className} – ${s.name}` }))}
            />
          </>
        }
      />
      {exams.data?.length === 0 || sections.data?.length === 0 ? (
        <Card>
          <Empty description={exams.data?.length === 0 ? 'No exams this year yet.' : 'No sections to show. Teachers see sections they teach or class-teach.'} />
        </Card>
      ) : (
        <Card
          title={data ? `${data.examName} · ${data.sectionLabel}` : undefined}
          extra={
            data && !noPapers && (
              <Space wrap>
                <Button icon={<DownloadOutlined />} onClick={exportCsv}>
                  CSV
                </Button>
                <Tooltip title={exam?.published ? undefined : 'Publish the exam to print its report cards'}>
                  <Button
                    type="primary"
                    icon={<FilePdfOutlined />}
                    disabled={!exam?.published || data.rows.length === 0}
                    loading={printing === 'all'}
                    onClick={() => print('all', { examId })}
                  >
                    All report cards
                  </Button>
                </Tooltip>
                <Tooltip title="Every published exam of the year and the weighted final result">
                  <Button icon={<FilePdfOutlined />} disabled={data.rows.length === 0} loading={printing === 'year'} onClick={() => print('year', {})}>
                    Annual cards
                  </Button>
                </Tooltip>
              </Space>
            )
          }
        >
          {data && !data.published && (
            <Typography.Paragraph type="secondary">Draft: results may still change until the exam is published.</Typography.Paragraph>
          )}
          {noPapers ? (
            <Empty description={`${data?.examName} has no papers for this class. Papers are set under Exam setup → Papers.`} />
          ) : (
          <Table<Row>
            rowKey="enrollmentId"
            size="small"
            loading={sheet.isLoading}
            dataSource={data?.rows ?? []}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: sheet.isError ? errorMessage(sheet.error) : 'No students' }}
            columns={[
              { title: 'Roll', dataIndex: 'rollNo', width: 56, fixed: 'left' },
              { title: 'Student', dataIndex: 'fullName', fixed: 'left' },
              ...(data?.subjects ?? []).map((s) => ({
                title: (
                  <>
                    {s.subjectName}
                    <br />
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      /{s.maxMarks}
                    </Typography.Text>
                  </>
                ),
                key: `s${s.subjectId}`,
                align: 'center' as const,
                render: (_: unknown, r: Row) => {
                  const sr = subjectOf(r, s.subjectId)
                  return (
                    <span style={{ color: sr?.passed === false ? '#cf1322' : undefined }}>
                      {markText(sr)}
                      {sr?.grade && <Typography.Text type="secondary"> {sr.grade}</Typography.Text>}
                    </span>
                  )
                },
              })),
              { title: 'Total', key: 't', align: 'right', render: (_, r) => `${r.result.total}/${r.result.maxTotal}` },
              { title: '%', key: 'p', align: 'right', render: (_, r) => r.result.percent ?? '–' },
              { title: 'Grade', key: 'g', align: 'center', render: (_, r) => r.result.grade ?? '–' },
              {
                title: 'Result',
                key: 'r',
                render: (_, r) =>
                  r.result.passed === null ? <Tag>Incomplete</Tag> : r.result.passed ? <Tag color="green">Pass</Tag> : <Tag color="red">Fail</Tag>,
              },
              { title: 'Rank', dataIndex: 'rank', align: 'center', render: (v: number | null) => v ?? '–' },
              {
                key: 'pdf',
                align: 'right',
                render: (_, r) => (
                  <Button
                    size="small"
                    icon={<FilePdfOutlined />}
                    disabled={!exam?.published}
                    loading={printing === `s${r.studentId}`}
                    onClick={() => print(`s${r.studentId}`, { examId, studentId: r.studentId })}
                    aria-label={`Report card for ${r.fullName}`}
                  />
                ),
              },
            ]}
          />
          )}
        </Card>
      )}
    </>
  )
}
