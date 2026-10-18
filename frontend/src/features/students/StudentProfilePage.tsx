import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App, Button, Card, DatePicker, Descriptions, Empty, Flex, Form, Input, InputNumber, Modal, Result, Skeleton, Space,
  Table, Tabs, Tag, Typography,
} from 'antd'
import { EditOutlined, LogoutOutlined, PhoneOutlined, SwapOutlined, UserAddOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { errorMessage, isNotFound } from '../../api/client'
import { studentsApi, type EnrollmentView, type StudentDetail } from '../../api/students'
import { useAuth } from '../../auth/useAuth'
import { useFormErrors } from '../../components/useFormErrors'
import { FEE_STAFF, PRINCIPAL_ADMIN } from '../../layout/menu'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate } from '../../utils/format'
import { StudentAttendanceTab } from '../attendance/StudentAttendanceTab'
import { StudentFeesTab } from '../fees/StudentFeesTab'
import { useYears } from '../settings/queries'
import { CATEGORIES, ENROLLMENT_STATUS, GENDERS, RELATIONS, STUDENT_STATUS, labelOf } from './labels'
import { SectionSelect } from './SectionSelect'
import { StudentPhoto } from './StudentPhoto'

type Dialog = 'section' | 'leave' | 'enroll' | null

function ageOn(dob: string) {
  const years = dayjs().diff(dayjs(dob), 'year')
  return `${years} years`
}

export function StudentProfilePage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const { hasAnyRole, me } = useAuth()
  const canManage = hasAnyRole(PRINCIPAL_ADMIN)
  const [dialog, setDialog] = useState<Dialog>(null)
  const { data: s, isLoading, error, refetch } = useQuery({
    queryKey: ['student', id],
    queryFn: () => studentsApi.get(id),
    enabled: Number.isFinite(id),
    retry: (count, e) => !isNotFound(e) && count < 1,
  })

  const back = <Button onClick={() => navigate('/students')}>All students</Button>
  if (!Number.isFinite(id) || isNotFound(error)) {
    return <Result status="404" title="Student not found" subTitle={canManage ? undefined : 'Teachers see only students of sections they teach.'} extra={back} />
  }
  if (error) {
    return (
      <Result
        status="error"
        title="Could not load this student"
        subTitle={errorMessage(error)}
        extra={[
          <Button key="retry" type="primary" onClick={() => refetch()}>
            Try again
          </Button>,
          back,
        ]}
      />
    )
  }
  if (isLoading || !s) return <Skeleton active avatar />

  const current = s.currentEnrollment
  const status = STUDENT_STATUS[s.status]
  const isActive = s.status === 'ACTIVE'

  return (
    <>
      <Card style={{ marginBottom: 16 }}>
        <Flex gap={16} wrap align="center">
          <StudentPhoto studentId={s.id} name={s.fullName} hasPhoto={s.hasPhoto} version={s.photoVersion} size={88} />
          <Flex vertical gap={4} style={{ flex: '1 1 220px', minWidth: 0 }}>
            <Typography.Title level={3} style={{ margin: 0 }}>
              {s.fullName} <Tag color={status.color}>{status.label}</Tag>
            </Typography.Title>
            <Typography.Text type="secondary">
              Adm. no. {s.admissionNo} · Admitted {formatDate(s.admissionDate)}
            </Typography.Text>
            <Typography.Text>
              {current ? (
                <>
                  {current.className} – {current.sectionName} · Roll {current.rollNo ?? '—'}{' '}
                  {current.status !== 'ACTIVE' && (
                    <Tag color={ENROLLMENT_STATUS[current.status].color}>{ENROLLMENT_STATUS[current.status].label}</Tag>
                  )}
                </>
              ) : (
                <Tag>Not enrolled this year</Tag>
              )}
            </Typography.Text>
          </Flex>
          {canManage && (
            <Space wrap>
              <Button icon={<EditOutlined />} onClick={() => navigate(`/students/${s.id}/edit`)}>
                Edit
              </Button>
              {isActive && current?.status === 'ACTIVE' && (
                <Button icon={<SwapOutlined />} onClick={() => setDialog('section')}>
                  Change section
                </Button>
              )}
              {isActive && !current && (
                <Button icon={<UserAddOutlined />} onClick={() => setDialog('enroll')}>
                  Enrol this year
                </Button>
              )}
              {isActive && (
                <Button danger icon={<LogoutOutlined />} onClick={() => setDialog('leave')}>
                  Mark as left
                </Button>
              )}
            </Space>
          )}
        </Flex>
      </Card>

      <Card>
        <Tabs
          items={[
            { key: 'details', label: 'Details', children: <DetailsTab s={s} /> },
            { key: 'history', label: 'Class history', children: <HistoryTab enrollments={s.enrollments} /> },
            ...(me?.school.modules.attendance
              ? [{ key: 'attendance', label: 'Attendance', children: <StudentAttendanceTab studentId={s.id} /> }]
              : []),
            ...(me?.school.modules.fees && hasAnyRole(FEE_STAFF)
              ? [{ key: 'fees', label: 'Fees', children: <StudentFeesTab studentId={s.id} /> }]
              : []),
            ...['Marks', 'Certificates'].map((label) => ({
              key: label.toLowerCase(),
              label,
              children: <Empty description={`${label} will appear here once the module is set up.`} />,
            })),
          ]}
        />
      </Card>

      {dialog === 'section' && current && (
        <SectionChangeDialog student={s} enrollment={current} onClose={() => setDialog(null)} />
      )}
      {dialog === 'leave' && <LeaveDialog student={s} onClose={() => setDialog(null)} />}
      {dialog === 'enroll' && <EnrollDialog student={s} onClose={() => setDialog(null)} />}
    </>
  )
}

function DetailsTab({ s }: { s: StudentDetail }) {
  return (
    <Flex vertical gap={24}>
      <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} size="small">
        <Descriptions.Item label="Date of birth">
          {formatDate(s.dateOfBirth)} ({ageOn(s.dateOfBirth)})
        </Descriptions.Item>
        <Descriptions.Item label="Gender">{labelOf(GENDERS, s.gender)}</Descriptions.Item>
        <Descriptions.Item label="Mother tongue">{s.motherTongue ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="Address" span={{ xs: 1, sm: 2, lg: 3 }}>
          {s.address ?? '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Identification marks">{s.identificationMarks ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="Previous school">{s.previousSchool ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="Previous TC no.">{s.previousTcNo ?? '—'}</Descriptions.Item>
        {s.sensitiveVisible && (
          <>
            <Descriptions.Item label="Aadhaar">{s.aadhaarMasked ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Category">{labelOf(CATEGORIES, s.socialCategory)}</Descriptions.Item>
            <Descriptions.Item label="Religion / caste">
              {[s.religion, s.caste].filter(Boolean).join(' / ') || '—'}
            </Descriptions.Item>
          </>
        )}
        {s.status === 'LEFT' && (
          <Descriptions.Item label="Left on">
            {formatDate(s.leftOn)} — {s.leavingReason}
          </Descriptions.Item>
        )}
      </Descriptions>

      <div>
        <Typography.Title level={5}>Parents / guardians</Typography.Title>
        <Flex gap={12} wrap>
          {s.guardians.map((g) => (
            <Card key={g.id} size="small" style={{ flex: '1 1 240px' }}>
              <Typography.Text strong>{g.fullName}</Typography.Text> {g.primary && <Tag color="blue">Primary</Tag>}
              <br />
              <Typography.Text type="secondary">
                {labelOf(RELATIONS, g.relation)}
                {g.occupation ? ` · ${g.occupation}` : ''}
              </Typography.Text>
              {g.phone && (
                <div>
                  <a href={`tel:${g.phone}`}>
                    <PhoneOutlined /> {g.phone}
                  </a>
                </div>
              )}
              {g.email && <div>{g.email}</div>}
            </Card>
          ))}
        </Flex>
      </div>
    </Flex>
  )
}

function HistoryTab({ enrollments }: { enrollments: EnrollmentView[] }) {
  return (
    <Table<EnrollmentView>
      rowKey="id"
      size="small"
      pagination={false}
      scroll={{ x: true }}
      dataSource={enrollments}
      columns={[
        { title: 'Year', dataIndex: 'academicYearName' },
        { title: 'Class', key: 'class', render: (_, e) => `${e.className} – ${e.sectionName}` },
        { title: 'Roll', dataIndex: 'rollNo', render: (r: number | null) => r ?? '—' },
        { title: 'Joined', dataIndex: 'joinedOn', render: formatDate },
        {
          title: 'Status',
          dataIndex: 'status',
          render: (st: EnrollmentView['status']) => <Tag color={ENROLLMENT_STATUS[st].color}>{ENROLLMENT_STATUS[st].label}</Tag>,
        },
      ]}
    />
  )
}

/** Shared plumbing for the profile's action dialogs: refresh the student and lists on success. */
function useStudentAction<T>(studentId: number, action: (values: T) => Promise<unknown>, done: string, onClose: () => void) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const showErrors = useFormErrors()
  const [form] = Form.useForm()
  const mutation = useMutation({
    mutationFn: action,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student', studentId] })
      queryClient.invalidateQueries({ queryKey: ['students'] })
      message.success(done)
      onClose()
    },
    onError: (e) => showErrors(form, e),
  })
  return { form, mutation }
}

function SectionChangeDialog({ student, enrollment, onClose }: { student: StudentDetail; enrollment: EnrollmentView; onClose: () => void }) {
  const { form, mutation } = useStudentAction<{ sectionId: number; rollNo?: number }>(
    student.id,
    (v) => studentsApi.changeSection(enrollment.id, { sectionId: v.sectionId, rollNo: v.rollNo ?? null }),
    'Section changed',
    onClose,
  )
  return (
    <Modal open title={`Change section: ${student.fullName}`} okText="Move" onCancel={onClose} onOk={() => form.submit()} confirmLoading={mutation.isPending}>
      <Typography.Paragraph type="secondary">
        Currently in {enrollment.className} – {enrollment.sectionName} ({enrollment.academicYearName}).
      </Typography.Paragraph>
      <Form form={form} layout="vertical" onFinish={(v) => mutation.mutate(v)}>
        <Form.Item name="sectionId" label="New section" rules={[{ required: true, message: 'Choose a section' }]}>
          <SectionSelect yearId={enrollment.academicYearId} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="rollNo" label="Roll no." extra="Leave empty for the next number in that section.">
          <InputNumber min={1} max={999} style={{ width: '100%' }} />
        </Form.Item>
        <button type="submit" hidden />
      </Form>
    </Modal>
  )
}

function LeaveDialog({ student, onClose }: { student: StudentDetail; onClose: () => void }) {
  const { form, mutation } = useStudentAction<{ leftOn: Dayjs; reason: string }>(
    student.id,
    (v) => studentsApi.leave(student.id, { leftOn: v.leftOn.format(API_DATE_FORMAT), reason: v.reason }),
    `${student.fullName} marked as left`,
    onClose,
  )
  return (
    <Modal
      open
      title={`Mark ${student.fullName} as left`}
      okText="Mark as left"
      okButtonProps={{ danger: true }}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={mutation.isPending}
    >
      <Typography.Paragraph type="secondary">
        The student is removed from their section's lists. Their records are kept. A transfer certificate can be issued
        later.
      </Typography.Paragraph>
      <Form form={form} layout="vertical" initialValues={{ leftOn: dayjs() }} onFinish={(v) => mutation.mutate(v)}>
        <Form.Item name="leftOn" label="Leaving date" rules={[{ required: true }]}>
          <DatePicker format={DATE_FORMAT} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="reason" label="Reason" rules={[{ required: true, whitespace: true }, { max: 300 }]}>
          <Input.TextArea rows={2} placeholder="e.g. Family moved to another city" />
        </Form.Item>
        <button type="submit" hidden />
      </Form>
    </Modal>
  )
}

function EnrollDialog({ student, onClose }: { student: StudentDetail; onClose: () => void }) {
  const currentYear = useYears().data?.find((y) => y.current)
  const { form, mutation } = useStudentAction<{ sectionId: number; rollNo?: number }>(
    student.id,
    (v) => studentsApi.enroll(student.id, { sectionId: v.sectionId, rollNo: v.rollNo ?? null }),
    'Student enrolled',
    onClose,
  )
  return (
    <Modal open title={`Enrol ${student.fullName} in ${currentYear?.name ?? ''}`} okText="Enrol" onCancel={onClose} onOk={() => form.submit()} confirmLoading={mutation.isPending}>
      <Form form={form} layout="vertical" onFinish={(v) => mutation.mutate(v)}>
        <Form.Item name="sectionId" label="Section" rules={[{ required: true, message: 'Choose a section' }]}>
          <SectionSelect yearId={currentYear?.id} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="rollNo" label="Roll no." extra="Leave empty for the next number.">
          <InputNumber min={1} max={999} style={{ width: '100%' }} />
        </Form.Item>
        <button type="submit" hidden />
      </Form>
    </Modal>
  )
}
