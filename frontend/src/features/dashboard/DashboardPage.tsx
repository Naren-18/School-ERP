import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Empty, Flex, List, Row, Skeleton, Tag, Typography } from 'antd'
import {
  CheckCircleFilled, ClockCircleOutlined, EditOutlined, ExclamationCircleFilled, ReloadOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { errorMessage } from '../../api/client'
import { dashboardApi, type Dashboard } from '../../api/dashboard'
import { useAuth } from '../../auth/useAuth'
import { PRINCIPAL_ADMIN } from '../../layout/menu'
import { formatDate, formatINR } from '../../utils/format'
import { AttendanceTrendChart, CollectionChart, StudentsByClassChart } from './charts'
import { SetupChecklist } from './SetupChecklist'

/** Text on the left, a value pushed to the right (wraps on narrow screens). */
function Line({ right, children }: { right: ReactNode; children: ReactNode }) {
  return (
    <Flex justify="space-between" align="baseline" gap={12} wrap style={{ width: '100%' }}>
      <span style={{ minWidth: 0 }}>{children}</span>
      {right}
    </Flex>
  )
}

/** A headline number with a short line under it. */
function Stat({ title, value, note, to }: { title: string; value: ReactNode; note?: ReactNode; to?: string }) {
  const body = (
    <Card size="small" hoverable={!!to} style={{ height: '100%' }} styles={{ body: { padding: '14px 16px' } }}>
      <Typography.Text type="secondary" style={{ fontSize: 13 }}>
        {title}
      </Typography.Text>
      <div style={{ fontSize: 26, fontWeight: 600, lineHeight: 1.3, margin: '2px 0', fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </div>
      {note && <div style={{ fontSize: 13 }}>{note}</div>}
    </Card>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

export function DashboardPage() {
  const { me, hasAnyRole } = useAuth()
  const adminLike = hasAnyRole(PRINCIPAL_ADMIN)
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardApi.get,
    refetchInterval: 5 * 60_000,
  })

  return (
    <>
      <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 16 }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Welcome, {me?.user.fullName}
          </Typography.Title>
          <Typography.Text type="secondary">
            {dayjs().format('dddd, DD-MM-YYYY')}
            {data?.academicYearName && ` · ${data.academicYearName}`}
          </Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={isFetching} onClick={() => refetch()} aria-label="Refresh" />
      </Flex>
      {adminLike && <SetupChecklist />}
      {isLoading ? (
        <Skeleton active />
      ) : isError || !data ? (
        <Alert type="error" showIcon title={errorMessage(error)} />
      ) : !data.academicYearName ? (
        !adminLike && (
          <Card>
            <Empty description="The school has not set up the academic year yet." />
          </Card>
        )
      ) : data.teacher ? (
        <TeacherDashboard data={data} />
      ) : (
        <SchoolDashboard data={data} adminLike={adminLike} />
      )}
    </>
  )
}

function SchoolDashboard({ data, adminLike }: { data: Dashboard; adminLike: boolean }) {
  const a = data.attendance
  const f = data.fees
  const marked = a ? a.sectionsToMark - a.notMarked.length : 0
  return (
    <Flex vertical gap={16}>
      <Row gutter={[12, 12]}>
        {data.students && (
          <Col xs={12} lg={6}>
            <Stat title="Students" value={data.students.total} note={<Typography.Text type="secondary">active this year</Typography.Text>} to="/students" />
          </Col>
        )}
        {a && (
          <Col xs={12} lg={6}>
            <Stat
              title="Attendance today"
              to="/attendance/today"
              value={a.nonWorkingReason ? '—' : a.percentage === null ? '—' : `${a.percentage}%`}
              note={
                a.nonWorkingReason ? (
                  <Typography.Text type="secondary">{a.nonWorkingReason}</Typography.Text>
                ) : a.notMarked.length === 0 && a.sectionsToMark > 0 ? (
                  <Typography.Text type="success">
                    <CheckCircleFilled /> all {a.sectionsToMark} sections marked
                  </Typography.Text>
                ) : (
                  <Typography.Text type="warning">
                    <ClockCircleOutlined /> {marked} of {a.sectionsToMark} sections marked
                  </Typography.Text>
                )
              }
            />
          </Col>
        )}
        {f && (
          <>
            <Col xs={12} lg={6}>
              <Stat
                title="Collected today"
                value={formatINR(f.collectedToday)}
                note={<Typography.Text type="secondary">{formatINR(f.collectedThisMonth)} this month</Typography.Text>}
                to="/fees/reports"
              />
            </Col>
            <Col xs={12} lg={6}>
              <Stat
                title="Dues outstanding"
                value={formatINR(f.outstanding)}
                note={
                  f.overdue > 0 ? (
                    <Typography.Text type="danger">
                      <ExclamationCircleFilled /> {formatINR(f.overdue)} overdue
                    </Typography.Text>
                  ) : (
                    <Typography.Text type="secondary">nothing overdue</Typography.Text>
                  )
                }
                to="/fees/reports"
              />
            </Col>
          </>
        )}
      </Row>

      <Row gutter={[16, 16]}>
        {a && (
          <Col xs={24} lg={12}>
            <Card title="Attendance, last 14 days" size="small" style={{ height: '100%' }}>
              {a.trend.some((d) => d.percentage !== null) ? (
                <AttendanceTrendChart data={a.trend} />
              ) : (
                <Empty description="No attendance marked in the last 14 days" />
              )}
            </Card>
          </Col>
        )}
        {f && (
          <Col xs={24} lg={12}>
            <Card title="Fees collected, last 14 days" size="small" style={{ height: '100%' }}>
              {f.trend.some((d) => d.amount > 0) ? <CollectionChart data={f.trend} /> : <Empty description="No receipts in the last 14 days" />}
            </Card>
          </Col>
        )}
        {data.students && (
          <Col xs={24} lg={12}>
            <Card title="Students by class" size="small">
              {data.students.total > 0 ? <StudentsByClassChart data={data.students.byClass} /> : <Empty description="No students enrolled yet" />}
            </Card>
          </Col>
        )}
        <Col xs={24} lg={12}>
          <Flex vertical gap={16}>
            {a && !a.nonWorkingReason && a.notMarked.length > 0 && (
              <Card title={`Attendance not marked yet (${a.notMarked.length})`} size="small">
                <Flex gap={6} wrap>
                  {a.notMarked.map((s) => (
                    <Link key={s.sectionId} to={`/attendance?section=${s.sectionId}`}>
                      <Tag style={{ padding: '2px 8px', fontSize: 13 }}>{s.label}</Tag>
                    </Link>
                  ))}
                </Flex>
              </Card>
            )}
            {f && (
              <Card title="Most overdue classes" size="small" extra={<Link to="/fees/reports">Dues report</Link>}>
                {f.topOverdueClasses.length === 0 ? (
                  <Typography.Text type="secondary">Nothing is overdue.</Typography.Text>
                ) : (
                  <List
                    size="small"
                    split={false}
                    dataSource={f.topOverdueClasses}
                    renderItem={(c) => (
                      <List.Item style={{ padding: '4px 0' }}>
                        <Line right={<b style={{ fontVariantNumeric: 'tabular-nums' }}>{formatINR(c.overdue)}</b>}>
                        {c.className} <Typography.Text type="secondary">· {c.students} students</Typography.Text>
                        </Line>
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            )}
            {data.upcomingExams && (
              <Card title="Upcoming exams" size="small" extra={adminLike && <Link to="/exams/setup">Exam setup</Link>}>
                {data.upcomingExams.length === 0 ? (
                  <Typography.Text type="secondary">No exams with upcoming dates.</Typography.Text>
                ) : (
                  <List
                    size="small"
                    split={false}
                    dataSource={data.upcomingExams}
                    renderItem={(e) => (
                      <List.Item style={{ padding: '4px 0' }}>
                        <Line right={e.published ? <Tag color="green">Published</Tag> : null}>
                        <b>{e.name}</b>{' '}
                        <Typography.Text type="secondary">
                          {formatDate(e.startDate)}
                          {e.endDate && e.endDate !== e.startDate && ` – ${formatDate(e.endDate)}`}
                        </Typography.Text>
                        </Line>
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            )}
            {data.recentAdmissions && (
              <Card title="Recent admissions" size="small" extra={adminLike && <Link to="/students/new">Admit student</Link>}>
                {data.recentAdmissions.length === 0 ? (
                  <Typography.Text type="secondary">No students yet.</Typography.Text>
                ) : (
                  <List
                    size="small"
                    split={false}
                    dataSource={data.recentAdmissions}
                    renderItem={(s) => (
                      <List.Item style={{ padding: '4px 0' }}>
                        <Line right={<Typography.Text type="secondary">{formatDate(s.admissionDate)}</Typography.Text>}>
                        <Link to={`/students/${s.studentId}`}>{s.fullName}</Link>{' '}
                        <Typography.Text type="secondary">· {s.className ?? 'not enrolled'}</Typography.Text>
                        </Line>
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            )}
          </Flex>
        </Col>
      </Row>
    </Flex>
  )
}

/** A teacher's day: their sections, attendance still to take, marks still to enter. */
function TeacherDashboard({ data }: { data: Dashboard }) {
  const t = data.teacher!
  const pendingAttendance = t.sections.filter((s) => s.marked === false)
  return (
    <Flex vertical gap={16}>
      <Row gutter={[12, 12]}>
        <Col xs={8}>
          <Stat title="My sections" value={t.sections.length} />
        </Col>
        <Col xs={8}>
          <Stat
            title="Attendance to take"
            value={t.nonWorkingReason || t.sections.every((s) => s.marked === null) ? '—' : pendingAttendance.length}
            to={pendingAttendance.length ? '/attendance' : undefined}
          />
        </Col>
        <Col xs={8}>
          <Stat title="Marks to enter" value={t.marksPending.length} to={t.marksPending.length ? '/exams' : undefined} />
        </Col>
      </Row>
      {t.nonWorkingReason && <Alert type="info" showIcon title={`Today: ${t.nonWorkingReason}`} />}
      <Card title="My sections" size="small">
        {t.sections.length === 0 ? (
          <Empty description="You have no sections this year. The principal assigns them under Setup → Teacher assignments." />
        ) : (
          <List
            dataSource={t.sections}
            renderItem={(s) => (
              <List.Item>
                <Line
                  right={
                    s.marked === false ? (
                      <Link to={`/attendance?section=${s.sectionId}`}>
                        <Button type="primary" size="small">
                          Take attendance
                        </Button>
                      </Link>
                    ) : s.marked ? (
                      <Typography.Text type="success">
                        <CheckCircleFilled /> Marked
                      </Typography.Text>
                    ) : null
                  }
                >
                  <b>{s.label}</b> {s.classTeacher && <Tag color="blue">Class teacher</Tag>}
                  <Typography.Text type="secondary"> · {s.students} students</Typography.Text>
                </Line>
              </List.Item>
            )}
          />
        )}
      </Card>
      <Card title="Marks to enter" size="small">
        {t.marksPending.length === 0 ? (
          <Typography.Text type="secondary">Nothing pending. Marks are entered while an exam is not yet published.</Typography.Text>
        ) : (
          <List
            dataSource={t.marksPending}
            renderItem={(m) => (
              <List.Item>
                <Line
                  right={
                    <Link to={`/exams?exam=${m.examId}&paper=${m.examSubjectId}&section=${m.sectionId}`}>
                      <Button size="small" icon={<EditOutlined />}>
                        Enter
                      </Button>
                    </Link>
                  }
                >
                  <b>{m.examName}</b> · {m.label} · {m.subjectName}{' '}
                  <Typography.Text type="secondary">
                    ({m.entered}/{m.students})
                  </Typography.Text>
                </Line>
              </List.Item>
            )}
          />
        )}
      </Card>
    </Flex>
  )
}
