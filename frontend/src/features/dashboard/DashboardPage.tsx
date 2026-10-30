import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Empty, Flex, Grid, List, Row, Segmented, Skeleton, Tag, Typography, theme } from 'antd'
import {
  CarryOutOutlined, CheckCircleFilled, CheckSquareOutlined, ClockCircleOutlined, DollarOutlined, EditOutlined,
  ExclamationCircleFilled, FieldTimeOutlined, ReloadOutlined, TeamOutlined, UserAddOutlined, WalletOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Role } from '../../api/auth'
import { errorMessage } from '../../api/client'
import { dashboardApi, type Dashboard, type PartAmount } from '../../api/dashboard'
import type { Modules } from '../../api/school'
import { useAuth } from '../../auth/useAuth'
import { FEE_STAFF, PRINCIPAL_ADMIN, TEACHING } from '../../layout/menu'
import { formatDate, formatINR } from '../../utils/format'
import {
  AttendanceTrendChart, CollectionChart, DonutChart, MonthlyCollectionChart, PercentBarChart, StudentsByClassChart, type Slice,
} from './charts'
import { CATEGORICAL, formatINRCompact, NEUTRAL } from './palette'
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

/** Colour of a stat tile's icon badge (the categorical slots, so tiles match the charts). */
const TILE = { blue: CATEGORICAL[0], orange: CATEGORICAL[1], green: CATEGORICAL[2], amber: CATEGORICAL[3], violet: CATEGORICAL[5] }

/** A headline number with an icon badge and a short line under it. */
function Stat({ title, value, note, to, icon, color }: {
  title: string
  value: ReactNode
  note?: ReactNode
  to?: string
  icon: ReactNode
  color: string
}) {
  // On a phone two tiles share a row: the badge goes above the text so the number keeps its width.
  const narrow = !Grid.useBreakpoint().sm
  const badge = narrow ? 30 : 38
  const body = (
    <Card size="small" hoverable={!!to} style={{ height: '100%' }} styles={{ body: { padding: narrow ? 12 : '14px 16px' } }}>
      <Flex gap={narrow ? 8 : 12} align="flex-start" vertical={narrow}>
        <div
          aria-hidden
          style={{
            width: badge,
            height: badge,
            borderRadius: 10,
            flexShrink: 0,
            display: 'grid',
            placeItems: 'center',
            fontSize: narrow ? 15 : 18,
            color,
            background: `${color}1f`,
          }}
        >
          {icon}
        </div>
        <div style={{ minWidth: 0, flex: 1 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {title}
          </Typography.Text>
          <div style={{ fontSize: 24, fontWeight: 600, lineHeight: 1.3, margin: '2px 0', fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}>
            {value}
          </div>
          {note && <div style={{ fontSize: 13 }}>{note}</div>}
        </div>
      </Flex>
    </Card>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

/** A small label above a group of cards, so the page reads in sections. */
function SectionTitle({ children }: { children: ReactNode }) {
  const { token } = theme.useToken()
  return (
    <Typography.Text
      strong
      style={{ display: 'block', fontSize: 12, letterSpacing: 0.6, textTransform: 'uppercase', color: token.colorTextSecondary, marginBottom: -4 }}
    >
      {children}
    </Typography.Text>
  )
}

const QUICK_ACTIONS: { label: string; to: string; icon: ReactNode; roles: readonly Role[]; module?: keyof Modules }[] = [
  { label: 'Mark attendance', to: '/attendance', icon: <CheckSquareOutlined />, roles: TEACHING, module: 'attendance' },
  { label: 'Collect fee', to: '/fees/collect', icon: <DollarOutlined />, roles: FEE_STAFF, module: 'fees' },
  { label: 'Admit student', to: '/students/new', icon: <UserAddOutlined />, roles: PRINCIPAL_ADMIN },
]

/** Greeting band in the school's colour, with the day and the user's most common actions. */
function Welcome({ yearName, refreshing, onRefresh }: { yearName: string | null; refreshing: boolean; onRefresh: () => void }) {
  const { me, hasAnyRole } = useAuth()
  const { token } = theme.useToken()
  const hour = dayjs().hour()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
  const primary = /^#[0-9a-f]{6}$/i.test(token.colorPrimary) ? token.colorPrimary : null
  const actions = QUICK_ACTIONS.filter((a) => hasAnyRole(a.roles) && (!a.module || me?.school.modules[a.module]))
  return (
    <Card
      style={{
        marginBottom: 16,
        background: primary ? `linear-gradient(120deg, ${primary}1a 0%, ${primary}08 45%, ${token.colorBgContainer} 100%)` : undefined,
        borderColor: primary ? `${primary}33` : undefined,
      }}
      styles={{ body: { padding: '16px 20px' } }}
    >
      <Flex justify="space-between" align="center" wrap gap={12}>
        <div style={{ minWidth: 0 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {greeting}, {me?.user.fullName}
          </Typography.Title>
          <Typography.Text type="secondary">
            {dayjs().format('dddd, DD-MM-YYYY')}
            {yearName && ` · Academic year ${yearName}`}
          </Typography.Text>
        </div>
        <Flex gap={8} wrap>
          {actions.map((a) => (
            <Link key={a.to} to={a.to}>
              <Button icon={a.icon}>{a.label}</Button>
            </Link>
          ))}
          <Button icon={<ReloadOutlined />} loading={refreshing} onClick={onRefresh} aria-label="Refresh" />
        </Flex>
      </Flex>
    </Card>
  )
}

export function DashboardPage() {
  const { hasAnyRole } = useAuth()
  const adminLike = hasAnyRole(PRINCIPAL_ADMIN)
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardApi.get,
    refetchInterval: 5 * 60_000,
  })

  return (
    <>
      <Welcome yearName={data?.academicYearName ?? null} refreshing={isFetching} onRefresh={() => refetch()} />
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

const MODE_COLOR: Record<string, string> = {
  CASH: CATEGORICAL[0], UPI: CATEGORICAL[1], CHEQUE: CATEGORICAL[2], CARD: CATEGORICAL[3], BANK: CATEGORICAL[4], ONLINE: CATEGORICAL[5],
}

/** Fee heads keep their colour (by id order); past five slices the rest fold into "Other". */
function headSlices(parts: PartAmount[]): Slice[] {
  const order = parts.map((p) => Number(p.key)).sort((x, y) => x - y)
  const top = parts
    .slice(0, 5)
    .map((p) => ({ key: p.key, label: p.label, value: p.amount, color: CATEGORICAL[order.indexOf(Number(p.key)) % 5] }))
  const rest = parts.slice(5).reduce((s, p) => s + p.amount, 0)
  return rest > 0 ? [...top, { key: 'other', label: 'Other', value: rest, color: NEUTRAL }] : top
}

function percentOf(part: number, whole: number) {
  return whole > 0 ? `${Math.round((part * 100) / whole)}%` : '—'
}

function SchoolDashboard({ data, adminLike }: { data: Dashboard; adminLike: boolean }) {
  const [collectionView, setCollectionView] = useState<'month' | 'day'>('month')
  const a = data.attendance
  const f = data.fees
  const r = data.latestResult
  const marked = a ? a.sectionsToMark - a.notMarked.length : 0
  const markedToday = a ? a.present + a.absent + a.leave + a.halfDay : 0
  const boys = data.students?.byClass.reduce((s, c) => s + c.boys, 0) ?? 0
  const girls = data.students?.byClass.reduce((s, c) => s + c.girls, 0) ?? 0
  // The donut row shares its width evenly, however many donuts there are.
  const donuts = (a ? 1 : 0) + (f ? 2 : 0) + (f && f.byHead.length > 1 ? 1 : 0)
  const donutSpan = 24 / Math.max(donuts, 1)
  return (
    <Flex vertical gap={16}>
      <Row gutter={[12, 12]}>
        {data.students && (
          <Col xs={12} lg={6}>
            <Stat
              title="Students"
              icon={<TeamOutlined />}
              color={TILE.blue}
              value={data.students.total}
              note={
                <Typography.Text type="secondary">
                  {boys} boys · {girls} girls
                </Typography.Text>
              }
              to="/students"
            />
          </Col>
        )}
        {a && (
          <Col xs={12} lg={6}>
            <Stat
              title="Attendance today"
              icon={<CarryOutOutlined />}
              color={TILE.green}
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
            <Col xs={24} sm={12} lg={6}>
              <Stat
                title="Collected today"
                icon={<WalletOutlined />}
                color={TILE.violet}
                value={formatINR(f.collectedToday)}
                note={<Typography.Text type="secondary">{formatINR(f.collectedThisMonth)} this month</Typography.Text>}
                to="/fees/reports"
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Stat
                title="Dues outstanding"
                icon={<FieldTimeOutlined />}
                color={TILE.orange}
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

      {/* Part-to-whole at a glance: today's attendance, the year's fees, how parents pay. */}
      {(a || f) && <SectionTitle>At a glance</SectionTitle>}
      <Row gutter={[16, 16]}>
        {a && (
          <Col xs={24} md={12} xl={donutSpan}>
            <Card title="Today's attendance" size="small" style={{ height: '100%' }} extra={<Link to="/attendance/today">Details</Link>}>
              {a.nonWorkingReason ? (
                <Empty description={`No school today: ${a.nonWorkingReason}`} />
              ) : markedToday === 0 ? (
                <Empty description="No attendance marked yet today" />
              ) : (
                <DonutChart
                  centerValue={a.percentage === null ? '—' : `${a.percentage}%`}
                  centerLabel="present"
                  slices={[
                    { key: 'P', label: 'Present', value: a.present, color: CATEGORICAL[0] },
                    { key: 'A', label: 'Absent', value: a.absent, color: CATEGORICAL[1] },
                    { key: 'L', label: 'On leave', value: a.leave, color: CATEGORICAL[2] },
                    { key: 'H', label: 'Half day', value: a.halfDay, color: CATEGORICAL[3] },
                  ]}
                />
              )}
            </Card>
          </Col>
        )}
        {f && (
          <Col xs={24} md={12} xl={donutSpan}>
            <Card title="Fees this year" size="small" style={{ height: '100%' }} extra={<Link to="/fees/reports">Dues report</Link>}>
              {f.collectedThisYear + f.outstanding === 0 ? (
                <Empty description="No fee plan for this year yet" />
              ) : (
                <DonutChart
                  centerValue={percentOf(f.collectedThisYear, f.collectedThisYear + f.outstanding)}
                  centerLabel="collected"
                  format={formatINRCompact}
                  slices={[
                    { key: 'paid', label: 'Collected', value: f.collectedThisYear, color: CATEGORICAL[0] },
                    { key: 'later', label: 'Not yet due', value: f.outstanding - f.overdue, color: CATEGORICAL[2] },
                    { key: 'overdue', label: 'Overdue', value: f.overdue, color: CATEGORICAL[1] },
                  ]}
                />
              )}
            </Card>
          </Col>
        )}
        {f && (
          <Col xs={24} md={12} xl={donutSpan}>
            <Card title="Payment modes this year" size="small" style={{ height: '100%' }}>
              {f.byMode.length === 0 ? (
                <Empty description="No receipts this year yet" />
              ) : (
                <DonutChart
                  centerValue={formatINRCompact(f.byMode.reduce((s, m) => s + m.amount, 0))}
                  centerLabel="received"
                  format={formatINRCompact}
                  slices={f.byMode.map((m) => ({ key: m.key, label: m.label, value: m.amount, color: MODE_COLOR[m.key] ?? NEUTRAL }))}
                />
              )}
            </Card>
          </Col>
        )}
        {f && f.byHead.length > 1 && (
          <Col xs={24} md={12} xl={donutSpan}>
            <Card title="Collected by fee head" size="small" style={{ height: '100%' }}>
              <DonutChart
                centerValue={String(f.byHead.length)}
                centerLabel="fee heads"
                format={formatINRCompact}
                slices={headSlices(f.byHead)}
              />
            </Card>
          </Col>
        )}
      </Row>

      {/* Change over time. */}
      {(a || f) && <SectionTitle>Trends</SectionTitle>}
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
            <Card
              title={collectionView === 'month' ? 'Fees collected per month' : 'Fees collected, last 14 days'}
              size="small"
              style={{ height: '100%' }}
              extra={
                <Segmented
                  size="small"
                  value={collectionView}
                  onChange={(v) => setCollectionView(v as 'month' | 'day')}
                  options={[
                    { label: 'Monthly', value: 'month' },
                    { label: 'Daily', value: 'day' },
                  ]}
                />
              }
            >
              {collectionView === 'month' ? (
                f.byMonth.some((m) => m.amount > 0) ? (
                  <MonthlyCollectionChart data={f.byMonth} />
                ) : (
                  <Empty description="No receipts this year yet" />
                )
              ) : f.trend.some((d) => d.amount > 0) ? (
                <CollectionChart data={f.trend} />
              ) : (
                <Empty description="No receipts in the last 14 days" />
              )}
            </Card>
          </Col>
        )}
      </Row>

      {/* Comparisons across classes and subjects. */}
      {(data.students || a || data.upcomingExams || f) && <SectionTitle>Classes &amp; exams</SectionTitle>}
      <Row gutter={[16, 16]}>
        {data.students && (
          <Col xs={24} lg={12}>
            <Card title="Students by class" size="small" style={{ height: '100%' }}>
              {data.students.total > 0 ? <StudentsByClassChart data={data.students.byClass} /> : <Empty description="No students enrolled yet" />}
            </Card>
          </Col>
        )}
        {a && (
          <Col xs={24} lg={12}>
            <Card title={`Attendance by class, ${dayjs().format('MMMM')}`} size="small" style={{ height: '100%' }}>
              {a.byClass.length > 0 ? (
                <PercentBarChart
                  data={a.byClass}
                  labelKey="className"
                  tooltip={(c) => ({ title: c.className, body: `${c.percentage}% present · ${c.marked} marked` })}
                />
              ) : (
                <Empty description="No attendance marked this month" />
              )}
            </Card>
          </Col>
        )}
        {data.upcomingExams && (
          <Col xs={24} lg={12}>
            <Card
              title={r ? `${r.examName}: subject averages` : 'Exam results'}
              size="small"
              style={{ height: '100%' }}
              extra={r && <Link to="/exams/results">Results</Link>}
            >
              {r && r.subjects.length > 0 ? (
                <PercentBarChart
                  data={r.subjects.map((s) => ({ ...s, percentage: s.averagePercent }))}
                  labelKey="subjectName"
                  tooltip={(s) => ({
                    title: s.subjectName,
                    body: `Average ${s.averagePercent ?? '—'}% · ${s.passPercent ?? '—'}% passed · ${s.students} students`,
                  })}
                />
              ) : (
                <Empty description="Subject averages appear once an exam is published" />
              )}
            </Card>
          </Col>
        )}
        {f && (
          <Col xs={24} lg={12}>
            <Card title="Most overdue classes" size="small" style={{ height: '100%' }} extra={<Link to="/fees/reports">Dues report</Link>}>
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
          </Col>
        )}
      </Row>

      {/* To-dos and recent activity. */}
      {((a && !a.nonWorkingReason && a.notMarked.length > 0) || data.upcomingExams || data.recentAdmissions) && (
        <SectionTitle>To do &amp; recent</SectionTitle>
      )}
      <Row gutter={[16, 16]}>
        {a && !a.nonWorkingReason && a.notMarked.length > 0 && (
          <Col xs={24} lg={12}>
            <Card title={`Attendance not marked yet (${a.notMarked.length})`} size="small" style={{ height: '100%' }}>
              <Flex gap={6} wrap>
                {a.notMarked.map((s) => (
                  <Link key={s.sectionId} to={`/attendance?section=${s.sectionId}`}>
                    <Tag style={{ padding: '2px 8px', fontSize: 13 }}>{s.label}</Tag>
                  </Link>
                ))}
              </Flex>
            </Card>
          </Col>
        )}
        {data.upcomingExams && (
          <Col xs={24} lg={12}>
            <Card title="Upcoming exams" size="small" style={{ height: '100%' }} extra={adminLike && <Link to="/exams/setup">Exam setup</Link>}>
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
          </Col>
        )}
        {data.recentAdmissions && (
          <Col xs={24} lg={12}>
            <Card title="Recent admissions" size="small" style={{ height: '100%' }} extra={adminLike && <Link to="/students/new">Admit student</Link>}>
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
          </Col>
        )}
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
        <Col xs={24} sm={8}>
          <Stat title="My sections" icon={<TeamOutlined />} color={TILE.blue} value={t.sections.length} />
        </Col>
        <Col xs={12} sm={8}>
          <Stat
            title="Attendance to take"
            icon={<CarryOutOutlined />}
            color={TILE.green}
            value={t.nonWorkingReason || t.sections.every((s) => s.marked === null) ? '—' : pendingAttendance.length}
            to={pendingAttendance.length ? '/attendance' : undefined}
          />
        </Col>
        <Col xs={12} sm={8}>
          <Stat
            title="Marks to enter"
            icon={<EditOutlined />}
            color={TILE.amber}
            value={t.marksPending.length}
            to={t.marksPending.length ? '/exams' : undefined}
          />
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
