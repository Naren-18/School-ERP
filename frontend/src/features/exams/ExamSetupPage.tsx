import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App, Button, Card, DatePicker, Drawer, Empty, Flex, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table,
  Tabs, Tag, Typography,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { useAcademicQueries } from './useAcademicQueries'
import { errorMessage } from '../../api/client'
import { examsApi, type Exam, type GradingScale, type Paper } from '../../api/exams'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate } from '../../utils/format'
import { YearSelect } from '../settings/YearSelect'

export function ExamSetupPage() {
  return (
    <>
      <PageHeader title="Exam setup" subtitle="Exams, their subject papers per class, and grading scales." />
      <Card>
        <Tabs
          items={[
            { key: 'exams', label: 'Exams', children: <ExamsTab /> },
            { key: 'scales', label: 'Grading scales', children: <ScalesTab /> },
          ]}
        />
      </Card>
    </>
  )
}

interface ExamForm {
  name: string
  gradingScaleId: number
  dates?: [Dayjs, Dayjs]
  displayOrder?: number
  weightage?: number | null
}

function ExamsTab() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<ExamForm>()
  const [yearId, setYearId] = useState<number>()
  const [editing, setEditing] = useState<Exam | 'new' | null>(null)
  const [papersFor, setPapersFor] = useState<Exam | null>(null)
  const exams = useQuery({ queryKey: ['exams', yearId], queryFn: () => examsApi.list(yearId), enabled: !!yearId })
  const scales = useQuery({ queryKey: ['grading-scales'], queryFn: examsApi.scales })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['exams'] })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: (v: ExamForm) =>
      examsApi.save(editing && editing !== 'new' ? editing.id : null, {
        academicYearId: yearId!,
        name: v.name,
        gradingScaleId: v.gradingScaleId,
        startDate: v.dates?.[0]?.format(API_DATE_FORMAT) ?? null,
        endDate: v.dates?.[1]?.format(API_DATE_FORMAT) ?? null,
        displayOrder: v.displayOrder ?? 0,
        weightage: v.weightage ?? null,
      }),
    onSuccess: () => {
      invalidate()
      setEditing(null)
    },
    onError: (e) => showErrors(form, e),
  })
  const publish = useMutation({
    mutationFn: (e: Exam) => examsApi.setPublished(e.id, !e.published),
    onSuccess: (e) => {
      invalidate()
      message.success(e.published ? `${e.name} published: marks are locked and report cards can be printed` : `${e.name} unpublished`)
    },
    onError,
  })
  const remove = useMutation({ mutationFn: examsApi.remove, onSuccess: invalidate, onError })

  const open = (e: Exam | 'new') => {
    setEditing(e)
    form.resetFields()
    if (e === 'new') {
      form.setFieldsValue({ displayOrder: (exams.data?.length ?? 0) + 1, gradingScaleId: scales.data?.[0]?.id })
    } else {
      form.setFieldsValue({
        ...e,
        dates: e.startDate && e.endDate ? [dayjs(e.startDate), dayjs(e.endDate)] : undefined,
      })
    }
  }

  return (
    <>
      <Flex gap={8} wrap style={{ marginBottom: 16 }}>
        <YearSelect value={yearId} onChange={setYearId} />
        <Button type="primary" icon={<PlusOutlined />} disabled={!yearId || !scales.data?.length} onClick={() => open('new')}>
          Add exam
        </Button>
        {scales.data?.length === 0 && <Typography.Text type="warning">Add a grading scale first (next tab).</Typography.Text>}
      </Flex>
      <Table<Exam>
        rowKey="id"
        size="small"
        loading={exams.isLoading}
        dataSource={exams.data ?? []}
        pagination={false}
        scroll={{ x: true }}
        locale={{ emptyText: 'No exams yet. Add FA1, SA1, Term 1…' }}
        columns={[
          {
            title: 'Exam',
            dataIndex: 'name',
            render: (n: string, e) => (
              <>
                {n} {e.published ? <Tag color="green">Published</Tag> : <Tag>Draft</Tag>}
              </>
            ),
          },
          { title: 'Dates', key: 'd', render: (_, e) => (e.startDate ? `${formatDate(e.startDate)} – ${formatDate(e.endDate)}` : '—') },
          { title: 'Weightage', dataIndex: 'weightage', render: (w: number | null) => (w ? `${w}%` : 'Not counted') },
          { title: 'Grading', dataIndex: 'gradingScaleName' },
          { title: 'Papers', dataIndex: 'papers' },
          {
            key: 'act',
            align: 'right',
            render: (_, e) => (
              <Space wrap>
                <Button size="small" onClick={() => setPapersFor(e)}>
                  Papers
                </Button>
                <Button size="small" disabled={e.published} onClick={() => open(e)}>
                  Edit
                </Button>
                <Popconfirm
                  title={e.published ? `Unpublish ${e.name}?` : `Publish ${e.name}?`}
                  description={e.published ? 'Marks can be changed again.' : 'Marks get locked; report cards can be printed.'}
                  onConfirm={() => publish.mutate(e)}
                >
                  <Button size="small" type={e.published ? 'default' : 'primary'}>
                    {e.published ? 'Unpublish' : 'Publish'}
                  </Button>
                </Popconfirm>
                <Popconfirm title={`Delete ${e.name}?`} onConfirm={() => remove.mutate(e.id)}>
                  <Button size="small" danger icon={<DeleteOutlined />} aria-label="Delete" />
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing === 'new' ? 'Add exam' : 'Edit exam'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 40 }]}>
            <Input placeholder="e.g. FA1, SA1, Term 1" />
          </Form.Item>
          <Form.Item name="gradingScaleId" label="Grading scale" rules={[{ required: true }]}>
            <Select options={(scales.data ?? []).map((s) => ({ value: s.id, label: s.name }))} />
          </Form.Item>
          <Form.Item name="dates" label="Exam dates">
            <DatePicker.RangePicker format={DATE_FORMAT} style={{ width: '100%' }} />
          </Form.Item>
          <Flex gap={12} wrap>
            <Form.Item name="weightage" label="Weightage in final result (%)" extra="e.g. FA 20, SA 80. Leave empty to not count it." style={{ flex: '1 1 200px' }}>
              <InputNumber min={0.01} max={100} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="displayOrder" label="Order" style={{ flex: '0 1 120px' }}>
              <InputNumber min={0} max={999} style={{ width: '100%' }} />
            </Form.Item>
          </Flex>
          <button type="submit" hidden />
        </Form>
      </Modal>
      {papersFor && <PapersDrawer exam={papersFor} onClose={() => setPapersFor(null)} />}
    </>
  )
}

/** Papers of an exam: one per class + subject, with max and pass marks. */
function PapersDrawer({ exam, onClose }: { exam: Exam; onClose: () => void }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { classes, subjectsOf } = useAcademicQueries(exam.academicYearId)
  const [classId, setClassId] = useState<number>()
  const activeClass = classId ?? classes[0]?.id
  const [applyForm] = Form.useForm<{ maxMarks: number; passMarks?: number }>()
  const [adding, setAdding] = useState(false)
  const [addForm] = Form.useForm<{ subjectId: number; maxMarks: number; passMarks?: number; examDate?: Dayjs }>()
  const papers = useQuery({
    queryKey: ['exam-papers', exam.id, activeClass],
    queryFn: () => examsApi.papers(exam.id, activeClass),
    enabled: !!activeClass,
  })
  const classSubjects = subjectsOf(activeClass)
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['exam-papers', exam.id] })
    queryClient.invalidateQueries({ queryKey: ['exams'] })
  }
  const onError = (e: unknown) => message.error(errorMessage(e))
  const apply = useMutation({
    mutationFn: (v: { maxMarks: number; passMarks?: number }) => examsApi.applyPapers(exam.id, { maxMarks: v.maxMarks, passMarks: v.passMarks ?? null }),
    onSuccess: (r) => {
      invalidate()
      message.success(r.created ? `${r.created} papers added across classes` : 'Every class subject already has a paper')
    },
    onError,
  })
  const save = useMutation({
    mutationFn: (v: { subjectId: number; maxMarks: number; passMarks?: number; examDate?: Dayjs }) =>
      examsApi.savePaper(exam.id, {
        classId: activeClass!,
        subjectId: v.subjectId,
        maxMarks: v.maxMarks,
        passMarks: v.passMarks ?? null,
        examDate: v.examDate?.format(API_DATE_FORMAT) ?? null,
      }),
    onSuccess: () => {
      invalidate()
      setAdding(false)
    },
    onError,
  })
  const remove = useMutation({ mutationFn: examsApi.deletePaper, onSuccess: invalidate, onError })
  const edit = (p: Paper) => {
    setAdding(true)
    addForm.setFieldsValue({ ...p, passMarks: p.passMarks ?? undefined, examDate: p.examDate ? dayjs(p.examDate) : undefined })
  }

  return (
    <Drawer open onClose={onClose} width={Math.min(720, window.innerWidth)} title={`${exam.name}: papers`}>
      {exam.published && <Typography.Paragraph type="warning">Published: unpublish to change papers.</Typography.Paragraph>}
      <Card size="small" title="Add papers for every class at once" style={{ marginBottom: 16 }}>
        <Form form={applyForm} layout="inline" initialValues={{ maxMarks: 50 }} onFinish={(v) => apply.mutate(v)} disabled={exam.published}>
          <Form.Item name="maxMarks" label="Max" rules={[{ required: true }]}>
            <InputNumber min={1} max={999} />
          </Form.Item>
          <Form.Item name="passMarks" label="Pass">
            <InputNumber min={0} max={999} />
          </Form.Item>
          <Button htmlType="submit" loading={apply.isPending}>
            Apply to all class subjects
          </Button>
        </Form>
      </Card>
      <Flex gap={8} wrap style={{ marginBottom: 12 }}>
        <Select style={{ minWidth: 160 }} value={activeClass} onChange={setClassId} options={classes.map((c) => ({ value: c.id, label: c.name }))} />
        <Button
          icon={<PlusOutlined />}
          disabled={exam.published || !activeClass}
          onClick={() => {
            addForm.resetFields()
            addForm.setFieldsValue({ maxMarks: 50 })
            setAdding(true)
          }}
        >
          Add or change a paper
        </Button>
      </Flex>
      <Table<Paper>
        rowKey="id"
        size="small"
        pagination={false}
        loading={papers.isLoading}
        dataSource={papers.data ?? []}
        locale={{ emptyText: <Empty description="No papers for this class" /> }}
        columns={[
          { title: 'Subject', dataIndex: 'subjectName' },
          { title: 'Max', dataIndex: 'maxMarks' },
          { title: 'Pass', dataIndex: 'passMarks', render: (v: number | null) => v ?? '—' },
          { title: 'Date', dataIndex: 'examDate', render: formatDate },
          {
            key: 'a',
            align: 'right',
            render: (_, p) => (
              <Space>
                <Button size="small" disabled={exam.published} onClick={() => edit(p)}>
                  Edit
                </Button>
                <Popconfirm title="Remove this paper?" onConfirm={() => remove.mutate(p.id)} disabled={exam.published}>
                  <Button size="small" danger disabled={exam.published} icon={<DeleteOutlined />} aria-label="Remove" />
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal open={adding} title="Paper" onCancel={() => setAdding(false)} onOk={() => addForm.submit()} confirmLoading={save.isPending} destroyOnHidden>
        <Form form={addForm} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="subjectId" label="Subject" rules={[{ required: true }]} extra="Subjects this class studies (Setup → Class subjects).">
            <Select options={classSubjects.map((s) => ({ value: s.subjectId, label: s.subjectName }))} />
          </Form.Item>
          <Flex gap={12} wrap>
            <Form.Item name="maxMarks" label="Max marks" rules={[{ required: true }]} style={{ flex: 1 }}>
              <InputNumber min={0.01} max={999} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="passMarks" label="Pass marks" style={{ flex: 1 }}>
              <InputNumber min={0} max={999} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </Flex>
          <Form.Item name="examDate" label="Date">
            <DatePicker format={DATE_FORMAT} style={{ width: '100%' }} />
          </Form.Item>
          <button type="submit" hidden />
        </Form>
      </Modal>
    </Drawer>
  )
}

function ScalesTab() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<{ name: string; bands: GradingScale['bands'] }>()
  const [editing, setEditing] = useState<GradingScale | null>(null)
  const { data: scales = [], isLoading } = useQuery({ queryKey: ['grading-scales'], queryFn: examsApi.scales })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['grading-scales'] })
  const preset = useMutation({ mutationFn: examsApi.addPreset, onSuccess: invalidate, onError: (e) => message.error(errorMessage(e)) })
  const save = useMutation({
    mutationFn: (v: { name: string; bands: GradingScale['bands'] }) => examsApi.saveScale(editing!.id, v),
    onSuccess: () => {
      invalidate()
      setEditing(null)
    },
    onError: (e) => showErrors(form, e),
  })
  const remove = useMutation({ mutationFn: examsApi.deleteScale, onSuccess: invalidate, onError: (e) => message.error(errorMessage(e)) })
  const has = (name: string) => scales.some((s) => s.name === name)

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Button icon={<PlusOutlined />} disabled={has('CBSE 9-point')} loading={preset.isPending} onClick={() => preset.mutate('CBSE_9_POINT')}>
          Add CBSE 9-point
        </Button>
        <Button icon={<PlusOutlined />} disabled={has('TS state CCE')} loading={preset.isPending} onClick={() => preset.mutate('TS_CCE')}>
          Add TS state CCE
        </Button>
      </Space>
      {scales.length === 0 && !isLoading && <Empty description="Add a preset scale, then adjust it if your school uses different bands." />}
      <Flex gap={16} wrap>
        {scales.map((s) => (
          <Card
            key={s.id}
            size="small"
            title={s.name}
            style={{ flex: '1 1 320px' }}
            extra={
              <Space>
                <Button size="small" onClick={() => { setEditing(s); form.setFieldsValue(s) }}>
                  Edit
                </Button>
                {!s.inUse && (
                  <Popconfirm title={`Delete ${s.name}?`} onConfirm={() => remove.mutate(s.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />} aria-label="Delete" />
                  </Popconfirm>
                )}
              </Space>
            }
          >
            <Table
              rowKey="grade"
              size="small"
              pagination={false}
              dataSource={s.bands}
              columns={[
                { title: 'Grade', dataIndex: 'grade' },
                { title: 'From %', dataIndex: 'minPercent' },
                { title: 'Point', dataIndex: 'gradePoint', render: (v: number | null) => v ?? '—' },
                { title: 'Remark', dataIndex: 'remark' },
              ]}
            />
          </Card>
        ))}
      </Flex>
      <Modal open={!!editing} title={`Edit ${editing?.name ?? ''}`} width={720} onCancel={() => setEditing(null)} onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnHidden>
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
            <Input />
          </Form.Item>
          <Typography.Paragraph type="secondary">A percentage gets the grade with the highest "from" it reaches. One grade must start at 0.</Typography.Paragraph>
          <Form.List name="bands">
            {(fields, { add, remove: drop }) => (
              <>
                {fields.map((f) => (
                  <Flex key={f.key} gap={8} wrap align="flex-start">
                    <Form.Item name={[f.name, 'grade']} rules={[{ required: true }]} style={{ width: 80 }}>
                      <Input placeholder="Grade" maxLength={5} />
                    </Form.Item>
                    <Form.Item name={[f.name, 'minPercent']} rules={[{ required: true }]}>
                      <InputNumber placeholder="From %" min={0} max={100} />
                    </Form.Item>
                    <Form.Item name={[f.name, 'maxPercent']} rules={[{ required: true }]}>
                      <InputNumber placeholder="To %" min={0} max={100} />
                    </Form.Item>
                    <Form.Item name={[f.name, 'gradePoint']}>
                      <InputNumber placeholder="Point" min={0} max={99} />
                    </Form.Item>
                    <Form.Item name={[f.name, 'remark']} style={{ flex: 1, minWidth: 120 }}>
                      <Input placeholder="Remark" maxLength={60} />
                    </Form.Item>
                    <Button type="text" danger icon={<DeleteOutlined />} onClick={() => drop(f.name)} aria-label="Remove grade" />
                  </Flex>
                ))}
                <Button type="dashed" icon={<PlusOutlined />} onClick={() => add()} block>
                  Add grade
                </Button>
              </>
            )}
          </Form.List>
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
