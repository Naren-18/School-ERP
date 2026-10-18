import { Link } from 'react-router-dom'
import { Card, Col, Empty, Flex, Progress, Row, Statistic, Typography } from 'antd'
import { CheckCircleFilled, ClockCircleOutlined } from '@ant-design/icons'
import { useAuth } from '../../auth/useAuth'
import { PRINCIPAL_ADMIN } from '../../layout/menu'
import {
  useClasses, useSections, useSubjects, useTeachers, useYearAssignments, useYears,
} from '../settings/queries'

/** Until the real dashboard (phase 8): a setup checklist for principal/admin. */
function SetupChecklist() {
  const years = useYears().data ?? []
  const currentYear = years.find((y) => y.current)
  const classes = useClasses().data ?? []
  const sections = useSections(currentYear?.id).data ?? []
  const subjects = useSubjects().data ?? []
  const teachers = useTeachers().data ?? []
  // A teacher can only be assigned to a subject mapped to the class, so any
  // assignment this year means both mapping and assigning have been done.
  const assignments = useYearAssignments(currentYear?.id).data ?? []
  const assignedSections = new Set(assignments.map((a) => a.sectionId)).size

  const steps = [
    { done: !!currentYear, label: 'Create the academic year', detail: currentYear?.name, to: '/settings/years' },
    { done: classes.length > 0, label: 'Add classes', detail: `${classes.length} classes`, to: '/settings/classes' },
    { done: teachers.length > 0, label: 'Add teachers', detail: `${teachers.length} teachers`, to: '/settings/users' },
    { done: sections.length > 0, label: 'Add sections', detail: `${sections.length} sections`, to: '/settings/classes' },
    { done: subjects.length > 0, label: 'Add subjects', detail: `${subjects.length} subjects`, to: '/settings/subjects' },
    {
      done: assignments.length > 0,
      label: 'Map subjects to classes & assign teachers',
      detail: `${assignedSections} of ${sections.length} sections assigned`,
      to: assignments.length > 0 ? '/settings/assignments' : '/settings/class-subjects',
    },
  ]
  const done = steps.filter((s) => s.done).length

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={14}>
        <Card title="Set up your school">
          <Progress percent={Math.round((done / steps.length) * 100)} style={{ marginBottom: 16 }} />
          <Flex vertical gap={12}>
            {steps.map((s) => (
              <Flex key={s.label} align="center" gap={12}>
                {s.done ? (
                  <CheckCircleFilled style={{ color: 'var(--ant-color-success)', fontSize: 18 }} />
                ) : (
                  <ClockCircleOutlined style={{ color: 'var(--ant-color-text-tertiary)', fontSize: 18 }} />
                )}
                <Link to={s.to} style={{ flex: 1 }}>
                  {s.label}
                </Link>
                {s.done && s.detail && <Typography.Text type="secondary">{s.detail}</Typography.Text>}
              </Flex>
            ))}
          </Flex>
        </Card>
      </Col>
      <Col xs={24} lg={10}>
        <Row gutter={[16, 16]}>
          <Col xs={12}>
            <Card>
              <Statistic title="Classes" value={classes.length} />
            </Card>
          </Col>
          <Col xs={12}>
            <Card>
              <Statistic title="Sections" value={sections.length} />
            </Card>
          </Col>
          <Col xs={12}>
            <Card>
              <Statistic title="Subjects" value={subjects.length} />
            </Card>
          </Col>
          <Col xs={12}>
            <Card>
              <Statistic title="Teachers" value={teachers.length} />
            </Card>
          </Col>
        </Row>
      </Col>
    </Row>
  )
}

export function DashboardPage() {
  const { me, hasAnyRole } = useAuth()
  return (
    <>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Welcome, {me?.user.fullName}
      </Typography.Title>
      {hasAnyRole(PRINCIPAL_ADMIN) ? (
        <SetupChecklist />
      ) : (
        <Card>
          <Empty description="Your dashboard will appear here." />
        </Card>
      )}
    </>
  )
}
