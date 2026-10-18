import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Checkbox, Col, DatePicker, Flex, Progress, Row, Statistic, Table, Tabs } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { feesApi, type ClassSummary, type DueRow } from '../../api/fees'
import { PageHeader } from '../../components/PageHeader'
import { downloadCsv } from '../../utils/csv'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate, formatINR } from '../../utils/format'
import { SectionSelect } from '../students/SectionSelect'
import { useYears } from '../settings/queries'
import { YearSelect } from '../settings/YearSelect'
import { MODES, modeLabel } from './labels'

export function FeeReportsPage() {
  return (
    <>
      <PageHeader title="Fee reports" />
      <Card>
        <Tabs
          items={[
            { key: 'collection', label: 'Collection', children: <CollectionTab /> },
            { key: 'dues', label: 'Dues', children: <DuesTab /> },
            { key: 'classes', label: 'By class', children: <ClassSummaryTab /> },
          ]}
        />
      </Card>
    </>
  )
}

function CollectionTab() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs(), dayjs()])
  const from = range[0].format(API_DATE_FORMAT)
  const to = range[1].format(API_DATE_FORMAT)
  const { data, isLoading } = useQuery({ queryKey: ['fee-reports', 'collection', from, to], queryFn: () => feesApi.collection(from, to) })
  return (
    <>
      <Flex gap={8} wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker
          allowClear={false}
          format={DATE_FORMAT}
          value={range}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(v) => v?.[0] && v[1] && setRange([v[0], v[1]])}
        />
        <Button
          icon={<DownloadOutlined />}
          disabled={!data?.days.length}
          onClick={() =>
            downloadCsv(`collection-${from}-to-${to}.csv`, ['Date', 'Mode', 'Receipts', 'Amount'],
              data!.days.map((d) => [formatDate(d.date), modeLabel(d.paymentMode), d.receipts, d.total]))
          }
        >
          Export CSV
        </Button>
      </Flex>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={6}>
          <Statistic title={`Collected (${data?.receipts ?? 0} receipts)`} value={formatINR(data?.total ?? 0)} loading={isLoading} />
        </Col>
        {MODES.filter((m) => data?.byMode[m.value]).map((m) => (
          <Col key={m.value} xs={12} md={4}>
            <Statistic title={m.label} value={formatINR(data!.byMode[m.value])} />
          </Col>
        ))}
      </Row>
      <Table
        rowKey={(d) => `${d.date}-${d.paymentMode}`}
        size="small"
        loading={isLoading}
        dataSource={data?.days ?? []}
        pagination={false}
        scroll={{ x: true }}
        locale={{ emptyText: 'No money received in this period' }}
        columns={[
          { title: 'Date', dataIndex: 'date', render: formatDate },
          { title: 'Mode', dataIndex: 'paymentMode', render: modeLabel },
          { title: 'Receipts', dataIndex: 'receipts' },
          { title: 'Amount', dataIndex: 'total', align: 'right', render: formatINR },
        ]}
      />
    </>
  )
}

function DuesTab() {
  const currentYear = useYears().data?.find((y) => y.current)
  const [sectionId, setSectionId] = useState<number>()
  const [overdueOnly, setOverdueOnly] = useState(true)
  const { data = [], isLoading } = useQuery({
    queryKey: ['fee-reports', 'dues', sectionId, overdueOnly],
    queryFn: () => feesApi.dues({ sectionId, overdueOnly }),
  })
  const sum = (f: (r: DueRow) => number) => data.reduce((s, r) => s + f(r), 0)
  return (
    <>
      <Flex gap={8} wrap align="center" style={{ marginBottom: 16 }}>
        <SectionSelect yearId={currentYear?.id} allowClear placeholder="All classes" value={sectionId} onChange={setSectionId} />
        <Checkbox checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)}>
          Overdue only
        </Checkbox>
        <Button
          icon={<DownloadOutlined />}
          disabled={!data.length}
          onClick={() =>
            downloadCsv('fee-dues.csv', ['Class', 'Roll', 'Adm. no.', 'Student', 'Phone', 'Total', 'Paid', 'Balance', 'Overdue'],
              data.map((r) => [r.classLabel, r.rollNo, r.admissionNo, r.fullName, r.guardianPhone, r.net, r.paid, r.balance, r.overdue]))
          }
        >
          Export CSV
        </Button>
      </Flex>
      <Table<DueRow>
        rowKey="studentId"
        size="small"
        loading={isLoading}
        dataSource={data}
        scroll={{ x: true }}
        pagination={{ pageSize: 50, hideOnSinglePage: true }}
        locale={{ emptyText: overdueOnly ? 'Nothing overdue' : 'Nothing due' }}
        summary={() =>
          data.length ? (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3}>
                <b>{data.length} students</b>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1} align="right">
                <b>{formatINR(sum((r) => r.balance))}</b>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={2} align="right">
                <b>{formatINR(sum((r) => r.overdue))}</b>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} />
            </Table.Summary.Row>
          ) : null
        }
        columns={[
          { title: 'Class', dataIndex: 'classLabel' },
          { title: 'Roll', dataIndex: 'rollNo', width: 60 },
          { title: 'Student', key: 'n', render: (_, r) => <Link to={`/students/${r.studentId}`}>{r.fullName}</Link> },
          { title: 'Balance', dataIndex: 'balance', align: 'right', render: formatINR },
          { title: 'Overdue', dataIndex: 'overdue', align: 'right', render: (v: number) => (v > 0 ? <b style={{ color: '#b91c1c' }}>{formatINR(v)}</b> : '—') },
          {
            key: 'act',
            align: 'right',
            render: (_, r) => (
              <Flex gap={8} justify="flex-end">
                {r.guardianPhone && <a href={`tel:${r.guardianPhone}`}>Call</a>}
                <Link to={`/fees/collect?student=${r.studentId}`}>Collect</Link>
              </Flex>
            ),
          },
        ]}
      />
    </>
  )
}

function ClassSummaryTab() {
  const [yearId, setYearId] = useState<number>()
  const { data = [], isLoading } = useQuery({
    queryKey: ['fee-reports', 'classes', yearId],
    queryFn: () => feesApi.classSummary(yearId),
    enabled: !!yearId,
  })
  return (
    <>
      <Flex gap={8} wrap style={{ marginBottom: 16 }}>
        <YearSelect value={yearId} onChange={setYearId} />
        <Button
          icon={<DownloadOutlined />}
          disabled={!data.length}
          onClick={() =>
            downloadCsv('fees-by-class.csv', ['Class', 'Students', 'Expected', 'Collected', 'Outstanding', 'Collected %'],
              data.map((c) => [c.className, c.students, c.expected, c.collected, c.outstanding, c.collectedPercent]))
          }
        >
          Export CSV
        </Button>
      </Flex>
      <Table<ClassSummary>
        rowKey="classId"
        size="small"
        loading={isLoading}
        dataSource={data}
        pagination={false}
        scroll={{ x: true }}
        locale={{ emptyText: 'No dues in this year yet' }}
        columns={[
          { title: 'Class', dataIndex: 'className' },
          { title: 'Students', dataIndex: 'students' },
          { title: 'Expected', dataIndex: 'expected', align: 'right', render: formatINR },
          { title: 'Collected', dataIndex: 'collected', align: 'right', render: formatINR },
          { title: 'Outstanding', dataIndex: 'outstanding', align: 'right', render: formatINR },
          {
            title: 'Collected',
            key: 'pct',
            width: 180,
            render: (_, c) => (c.collectedPercent === null ? '—' : <Progress percent={c.collectedPercent} size="small" />),
          },
        ]}
      />
    </>
  )
}
