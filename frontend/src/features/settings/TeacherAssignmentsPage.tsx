import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Card, Empty, Select, Table, Typography } from 'antd'
import { academicApi, type ClassSubject } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { keys, useSections, useTeachers } from './queries'
import { YearSelect } from './YearSelect'

/** For one section: each subject the class studies, with a teacher picker. Saves on change. */
export function TeacherAssignmentsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [yearId, setYearId] = useState<number>()
  const [chosenSectionId, setSectionId] = useState<number>()
  const { data: sections = [] } = useSections(yearId)
  const { data: teachers = [] } = useTeachers()
  // Falls back to the first section when none is chosen or the year changed.
  const section = sections.find((s) => s.id === chosenSectionId) ?? sections[0]
  const sectionId = section?.id

  const subjects = useQuery({
    queryKey: keys.classSubjects(yearId, section?.classId),
    queryFn: () => academicApi.classSubjects(yearId!, section!.classId),
    enabled: !!yearId && !!section,
  })
  const assignments = useQuery({
    queryKey: keys.assignments(sectionId),
    queryFn: () => academicApi.assignments({ sectionId }),
    enabled: !!sectionId,
  })

  // Prefix match: refreshes this section's list and the year-wide one (dashboard).
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['assignments'] })
  const onError = (e: unknown) => message.error(errorMessage(e))
  const assign = useMutation({
    mutationFn: academicApi.assign,
    onSuccess: () => {
      invalidate()
      message.success('Teacher assigned')
    },
    onError,
  })
  const unassign = useMutation({ mutationFn: academicApi.unassign, onSuccess: invalidate, onError })

  const bySubject = new Map((assignments.data ?? []).map((a) => [a.subjectId, a]))

  return (
    <>
      <PageHeader
        title="Teacher assignments"
        subtitle="Teachers can enter attendance and marks only for what they are assigned here."
        extra={
          <>
            <YearSelect value={yearId} onChange={setYearId} />
            <Select
              style={{ minWidth: 160 }}
              value={sectionId}
              onChange={setSectionId}
              placeholder="Section"
              showSearch
              optionFilterProp="label"
              options={sections.map((s) => ({ value: s.id, label: `${s.className} – ${s.name}` }))}
            />
          </>
        }
      />
      {sections.length === 0 ? (
        <Card>
          <Empty description="Add sections for this year first (Classes & sections)." />
        </Card>
      ) : (
        <Card
          title={section ? `${section.className} – ${section.name}` : undefined}
          extra={section && <Typography.Text type="secondary">Class teacher: {section.classTeacherName ?? '—'}</Typography.Text>}
        >
          <Table<ClassSubject>
            rowKey="subjectId"
            loading={subjects.isLoading || assignments.isLoading}
            dataSource={subjects.data ?? []}
            pagination={false}
            scroll={{ x: true }}
            locale={{ emptyText: 'No subjects for this class yet (Class subjects).' }}
            columns={[
              { title: 'Subject', dataIndex: 'subjectName' },
              {
                title: 'Teacher',
                key: 'teacher',
                render: (_, cs) => {
                  const current = bySubject.get(cs.subjectId)
                  return (
                    <Select
                      style={{ width: '100%', minWidth: 200 }}
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      placeholder="Not assigned"
                      value={current?.teacherId}
                      disabled={assign.isPending || unassign.isPending}
                      onChange={(teacherId?: number) => {
                        if (teacherId) assign.mutate({ sectionId: sectionId!, subjectId: cs.subjectId, teacherId })
                        else if (current) unassign.mutate(current.id)
                      }}
                      options={teachers.map((t) => ({ value: t.id, label: t.fullName }))}
                    />
                  )
                },
              },
            ]}
          />
        </Card>
      )}
    </>
  )
}
