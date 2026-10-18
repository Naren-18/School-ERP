import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Checkbox, Empty, Flex, Select, Skeleton, Typography } from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons'
import { academicApi } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { keys, useClasses, useSubjects } from './queries'
import { SubjectTypeTag } from './SubjectTypeTag'
import { YearSelect } from './YearSelect'

/** Pick a year and class, tick its subjects, and order them for report cards. */
export function ClassSubjectsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [yearId, setYearId] = useState<number>()
  const [chosenClassId, setChosenClassId] = useState<number>()
  /** Unsaved edits; null means "show what is saved". */
  const [draft, setDraft] = useState<number[] | null>(null)
  const { data: classes = [] } = useClasses()
  const { data: subjects = [] } = useSubjects()
  const classId = chosenClassId ?? classes[0]?.id

  const mapping = useQuery({
    queryKey: keys.classSubjects(yearId, classId),
    queryFn: () => academicApi.classSubjects(yearId!, classId!),
    enabled: !!yearId && !!classId,
  })
  const saved = mapping.data?.map((cs) => cs.subjectId) ?? []
  const selected = draft ?? saved

  const save = useMutation({
    mutationFn: () => academicApi.setClassSubjects({ academicYearId: yearId!, classId: classId!, subjectIds: selected }),
    onSuccess: (data) => {
      queryClient.setQueryData(keys.classSubjects(yearId, classId), data)
      queryClient.invalidateQueries({ queryKey: ['assignments'] })
      setDraft(null)
      message.success('Subjects saved')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const changeYear = (id: number) => {
    setYearId(id)
    setDraft(null)
  }
  const changeClass = (id: number) => {
    setChosenClassId(id)
    setDraft(null)
  }
  const toggle = (id: number, on: boolean) => setDraft(on ? [...selected, id] : selected.filter((x) => x !== id))
  const move = (index: number, delta: number) => {
    const next = [...selected]
    const [item] = next.splice(index, 1)
    next.splice(index + delta, 0, item)
    setDraft(next)
  }

  const subjectById = new Map(subjects.map((s) => [s.id, s]))
  const dirty = JSON.stringify(saved) !== JSON.stringify(selected)

  return (
    <>
      <PageHeader
        title="Class subjects"
        subtitle="Which subjects each class studies this year. Order is used on report cards."
        extra={
          <>
            <YearSelect value={yearId} onChange={changeYear} />
            <Select
              style={{ minWidth: 140 }}
              value={classId}
              onChange={changeClass}
              placeholder="Class"
              options={classes.map((c) => ({ value: c.id, label: c.name }))}
            />
          </>
        }
      />
      {subjects.length === 0 || classes.length === 0 ? (
        <Card>
          <Empty description="Add classes and subjects first." />
        </Card>
      ) : mapping.isLoading ? (
        <Skeleton active />
      ) : (
        <Flex gap={16} wrap align="flex-start">
          <Card title="All subjects" style={{ flex: '1 1 280px' }}>
            <Flex vertical gap={8}>
              {subjects.map((s) => (
                <Checkbox key={s.id} checked={selected.includes(s.id)} onChange={(e) => toggle(s.id, e.target.checked)}>
                  {s.name} <SubjectTypeTag type={s.type} />
                </Checkbox>
              ))}
            </Flex>
          </Card>
          <Card
            title={`Taught in ${classes.find((c) => c.id === classId)?.name ?? ''} (${selected.length})`}
            style={{ flex: '1 1 280px' }}
            extra={
              <Button type="primary" disabled={!dirty} loading={save.isPending} onClick={() => save.mutate()}>
                Save
              </Button>
            }
          >
            {selected.length === 0 ? (
              <Typography.Text type="secondary">Tick subjects on the left.</Typography.Text>
            ) : (
              <Flex vertical gap={6}>
                {selected.map((id, i) => (
                  <Flex key={id} align="center" justify="space-between" gap={8}>
                    <Typography.Text>
                      {i + 1}. {subjectById.get(id)?.name}
                    </Typography.Text>
                    <span>
                      <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up" />
                      <Button
                        size="small"
                        type="text"
                        icon={<ArrowDownOutlined />}
                        disabled={i === selected.length - 1}
                        onClick={() => move(i, 1)}
                        aria-label="Move down"
                      />
                    </span>
                  </Flex>
                ))}
              </Flex>
            )}
            {saved.some((id) => !selected.includes(id)) && (
              <Typography.Paragraph type="warning" style={{ marginTop: 12, marginBottom: 0 }}>
                Removing a subject also removes its teacher assignments for this class.
              </Typography.Paragraph>
            )}
          </Card>
        </Flex>
      )}
    </>
  )
}
