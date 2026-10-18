import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, DatePicker, Empty, List, Progress, Row, Skeleton, Statistic, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { attendanceApi, type Today } from '../../api/attendance'
import { PageHeader } from '../../components/PageHeader'
import { API_DATE_FORMAT, DATE_FORMAT } from '../../utils/format'
import { STATUS, formatPercent } from './statusStyle'

type ClassRow = Today['classes'][number]

/** School-wide attendance for a day: overall, by class, and which sections still need marking. */
export function TodayPage() {
  const [date, setDate] = useState(dayjs().format(API_DATE_FORMAT))
  const { data, isLoading } = useQuery({ queryKey: ['attendance-today', date], queryFn: () => attendanceApi.today(date) })

  return (
    <>
      <PageHeader
        title="Attendance summary"
        extra={
          <DatePicker
            allowClear={false}
            format={DATE_FORMAT}
            value={dayjs(date)}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d.format(API_DATE_FORMAT))}
          />
        }
      />
      {isLoading || !data ? (
        <Skeleton active />
      ) : (
        <>
          {data.nonWorkingReason && <Alert type="info" showIcon style={{ marginBottom: 16 }} title={`Not a school day: ${data.nonWorkingReason}`} />}
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={24} md={6}>
              <Card>
                <Statistic title="Attendance" value={formatPercent(data.counts.percentage)} />
                <Progress percent={data.counts.percentage ?? 0} showInfo={false} strokeColor={STATUS.P.color} />
              </Card>
            </Col>
            {(
              [
                ['Present', data.counts.present + data.counts.halfDay, STATUS.P.color],
                ['Absent', data.counts.absent, STATUS.A.color],
                ['On leave', data.counts.leave, STATUS.L.color],
                ['Not marked', data.counts.unmarked, undefined],
              ] as const
            ).map(([title, value, color]) => (
              <Col key={title} xs={12} md={4}>
                <Card>
                  <Statistic title={title} value={value} styles={{ content: { color } }} />
                </Card>
              </Col>
            ))}
          </Row>
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={16}>
              <Card title="By class">
                <Table<ClassRow>
                  rowKey="classId"
                  size="small"
                  pagination={false}
                  scroll={{ x: true }}
                  dataSource={data.classes}
                  locale={{ emptyText: 'No sections this year' }}
                  columns={[
                    { title: 'Class', dataIndex: 'className' },
                    { title: 'Marked', key: 'm', render: (_, c) => `${c.markedSections} / ${c.sections}` },
                    { title: 'Students', dataIndex: 'enrolled' },
                    { title: 'Absent', key: 'a', render: (_, c) => c.counts.absent },
                    {
                      title: 'Attendance',
                      key: 'pct',
                      render: (_, c) =>
                        c.counts.percentage === null ? '—' : <Progress percent={c.counts.percentage} size="small" strokeColor={STATUS.P.color} />,
                    },
                  ]}
                />
              </Card>
            </Col>
            <Col xs={24} lg={8}>
              <Card title={`Not marked yet (${data.unmarkedSections.length})`}>
                {data.unmarkedSections.length === 0 ? (
                  <Empty description={data.nonWorkingReason ? 'No marking needed today' : 'Every section is marked'} />
                ) : (
                  <List
                    size="small"
                    dataSource={data.unmarkedSections}
                    renderItem={(s) => (
                      <List.Item extra={<Typography.Text type="secondary">{s.enrolled} students</Typography.Text>}>
                        <Link to={`/attendance?section=${s.sectionId}`}>{s.label}</Link>
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            </Col>
          </Row>
        </>
      )}
    </>
  )
}
