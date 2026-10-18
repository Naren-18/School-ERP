import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Button, Card, Flex, Input, Select, Table, Tag, Typography } from 'antd'
import { PhoneOutlined, UploadOutlined, UserAddOutlined } from '@ant-design/icons'
import { studentsApi, type StudentListItem, type StudentStatus } from '../../api/students'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { PRINCIPAL_ADMIN } from '../../layout/menu'
import { useYears } from '../settings/queries'
import { STUDENT_STATUS } from './labels'
import { SectionSelect } from './SectionSelect'
import { StudentPhoto } from './StudentPhoto'

const PAGE_SIZE = 20

export function StudentListPage() {
  const navigate = useNavigate()
  const { hasAnyRole } = useAuth()
  const canManage = hasAnyRole(PRINCIPAL_ADMIN)
  const currentYear = useYears().data?.find((y) => y.current)
  const [q, setQ] = useState('')
  const [sectionId, setSectionId] = useState<number>()
  const [status, setStatus] = useState<StudentStatus>('ACTIVE')
  const [page, setPage] = useState(1)

  const params = { q: q || undefined, sectionId, status, page: page - 1, size: PAGE_SIZE }
  const { data, isFetching } = useQuery({
    queryKey: ['students', params],
    queryFn: () => studentsApi.search(params),
    placeholderData: keepPreviousData,
  })

  const reset = () => setPage(1)

  return (
    <>
      <PageHeader
        title="Students"
        subtitle={currentYear ? `Academic year ${currentYear.name}` : undefined}
        extra={
          canManage && (
            <>
              <Button icon={<UploadOutlined />} onClick={() => navigate('/students/import')}>
                Import CSV
              </Button>
              <Button type="primary" icon={<UserAddOutlined />} onClick={() => navigate('/students/new')}>
                Admit student
              </Button>
            </>
          )
        }
      />
      <Card>
        <Flex gap={8} wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="Name, admission no. or parent's phone"
            style={{ maxWidth: 320 }}
            onSearch={(v) => {
              setQ(v.trim())
              reset()
            }}
          />
          <SectionSelect
            yearId={currentYear?.id}
            allowClear
            placeholder="All classes"
            value={sectionId}
            onChange={(v) => {
              setSectionId(v)
              reset()
            }}
          />
          <Select<StudentStatus>
            style={{ minWidth: 130 }}
            value={status}
            onChange={(v) => {
              setStatus(v)
              reset()
            }}
            options={Object.entries(STUDENT_STATUS).map(([value, s]) => ({ value: value as StudentStatus, label: s.label }))}
          />
        </Flex>
        <Table<StudentListItem>
          rowKey="id"
          loading={isFetching}
          dataSource={data?.content ?? []}
          scroll={{ x: true }}
          onRow={(s) => ({ onClick: () => navigate(`/students/${s.id}`), style: { cursor: 'pointer' } })}
          locale={{ emptyText: q ? 'No students match your search' : 'No students yet' }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.totalElements ?? 0,
            onChange: setPage,
            showSizeChanger: false,
            showTotal: (total) => `${total} students`,
          }}
          columns={[
            {
              title: 'Student',
              key: 'name',
              render: (_, s) => (
                <Flex align="center" gap={10}>
                  {/* Initials only: loading 20 private photos per page is not worth it. */}
                  <StudentPhoto studentId={s.id} name={s.fullName} hasPhoto={false} size={36} />
                  <Flex vertical>
                    <Typography.Text strong>{s.fullName}</Typography.Text>
                    <Typography.Text type="secondary">Adm. {s.admissionNo}</Typography.Text>
                  </Flex>
                </Flex>
              ),
            },
            {
              title: 'Class',
              key: 'class',
              render: (_, s) => (s.className ? `${s.className} – ${s.sectionName}` : <Tag>Not enrolled</Tag>),
            },
            { title: 'Roll', dataIndex: 'rollNo', render: (r: number | null) => r ?? '—' },
            {
              title: 'Parent',
              key: 'guardian',
              render: (_, s) =>
                s.guardianName ? (
                  <Flex vertical>
                    <span>{s.guardianName}</span>
                    {s.guardianPhone && (
                      <a href={`tel:${s.guardianPhone}`} onClick={(e) => e.stopPropagation()}>
                        <PhoneOutlined /> {s.guardianPhone}
                      </a>
                    )}
                  </Flex>
                ) : (
                  '—'
                ),
            },
            {
              title: 'Status',
              dataIndex: 'status',
              render: (st: StudentStatus) => <Tag color={STUDENT_STATUS[st].color}>{STUDENT_STATUS[st].label}</Tag>,
            },
          ]}
        />
      </Card>
    </>
  )
}
