import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Alert, App, Button, Card, Col, Empty, Flex, Form, Input, InputNumber, List, Modal, Popconfirm, Row, Select, Space,
  Tag, Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { academicApi, type SchoolClass, type Section } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { keys, useClasses, useSections, useTeachers } from './queries'
import { YearSelect } from './YearSelect'

interface SectionForm {
  name: string
  classTeacherId: number | null
  capacity: number | null
}

export function ClassesSectionsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [yearId, setYearId] = useState<number>()
  const [classForm] = Form.useForm<Omit<SchoolClass, 'id'>>()
  const [sectionForm] = Form.useForm<SectionForm>()
  const [editingClass, setEditingClass] = useState<SchoolClass | 'new' | null>(null)
  const [editingSection, setEditingSection] = useState<{ cls: SchoolClass; section?: Section } | null>(null)

  const { data: classes = [], isLoading } = useClasses()
  const { data: sections = [] } = useSections(yearId)
  const { data: teachers = [] } = useTeachers()

  const sectionsByClass = useMemo(() => {
    const map = new Map<number, Section[]>()
    sections.forEach((s) => map.set(s.classId, [...(map.get(s.classId) ?? []), s]))
    return map
  }, [sections])

  const onError = (e: unknown) => message.error(errorMessage(e))

  const saveClass = useMutation({
    mutationFn: (v: Omit<SchoolClass, 'id'>) =>
      editingClass && editingClass !== 'new' ? academicApi.updateClass(editingClass.id, v) : academicApi.createClass(v),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.classes })
      queryClient.invalidateQueries({ queryKey: ['sections'] })
      setEditingClass(null)
    },
    onError: (e) => showErrors(classForm, e),
  })
  const deleteClass = useMutation({
    mutationFn: academicApi.deleteClass,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.classes }),
    onError,
  })

  const saveSection = useMutation({
    mutationFn: (v: SectionForm) => {
      const body = { ...v, academicYearId: yearId!, classId: editingSection!.cls.id }
      const existing = editingSection?.section
      return existing ? academicApi.updateSection(existing.id, body) : academicApi.createSection(body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.sections(yearId) })
      setEditingSection(null)
    },
    onError: (e) => showErrors(sectionForm, e),
  })
  const deleteSection = useMutation({
    mutationFn: academicApi.deleteSection,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.sections(yearId) }),
    onError,
  })

  const openClass = (c: SchoolClass | 'new') => {
    setEditingClass(c)
    classForm.resetFields()
    classForm.setFieldsValue(
      c === 'new' ? { name: '', displayOrder: (classes.at(-1)?.displayOrder ?? 0) + 1 } : c,
    )
  }
  const openSection = (cls: SchoolClass, section?: Section) => {
    setEditingSection({ cls, section })
    sectionForm.resetFields()
    if (section) sectionForm.setFieldsValue(section)
  }

  return (
    <>
      <PageHeader
        title="Classes & sections"
        subtitle="Classes are permanent; sections belong to an academic year."
        extra={
          <>
            <YearSelect value={yearId} onChange={setYearId} />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openClass('new')}>
              Add class
            </Button>
          </>
        }
      />
      {!yearId && !isLoading && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} title="Create an academic year to add sections." />
      )}
      {classes.length === 0 && !isLoading ? (
        <Card>
          <Empty description="No classes yet. Add Nursery, LKG, Class 1 … in teaching order." />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {classes.map((cls) => (
            <Col key={cls.id} xs={24} md={12} xl={8}>
              <Card
                size="small"
                title={cls.name}
                extra={
                  <Space size={4}>
                    <Button size="small" type="text" onClick={() => openClass(cls)}>
                      Edit
                    </Button>
                    <Popconfirm title={`Delete ${cls.name}?`} onConfirm={() => deleteClass.mutate(cls.id)}>
                      <Button size="small" type="text" danger>
                        Delete
                      </Button>
                    </Popconfirm>
                  </Space>
                }
              >
                <List
                  size="small"
                  dataSource={sectionsByClass.get(cls.id) ?? []}
                  locale={{ emptyText: 'No sections this year' }}
                  renderItem={(s) => (
                    <List.Item
                      actions={[
                        <a key="edit" onClick={() => openSection(cls, s)}>
                          Edit
                        </a>,
                        <Popconfirm key="del" title={`Delete section ${s.name}?`} onConfirm={() => deleteSection.mutate(s.id)}>
                          <a style={{ color: 'var(--ant-color-error)' }}>Delete</a>
                        </Popconfirm>,
                      ]}
                    >
                      <Flex vertical>
                        <Typography.Text strong>
                          {cls.name} – {s.name}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                          {s.classTeacherName ?? 'No class teacher'}
                          {s.capacity ? ` · capacity ${s.capacity}` : ''}
                        </Typography.Text>
                      </Flex>
                    </List.Item>
                  )}
                />
                <Button
                  block
                  type="dashed"
                  icon={<PlusOutlined />}
                  disabled={!yearId}
                  onClick={() => openSection(cls)}
                  style={{ marginTop: 8 }}
                >
                  Add section
                </Button>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Modal
        title={editingClass === 'new' ? 'Add class' : 'Edit class'}
        open={editingClass !== null}
        onCancel={() => setEditingClass(null)}
        onOk={() => classForm.submit()}
        okText="Save"
        confirmLoading={saveClass.isPending}
        destroyOnHidden
      >
        <Form form={classForm} layout="vertical" onFinish={(v) => saveClass.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 30 }]}>
            <Input placeholder="e.g. Class 5" />
          </Form.Item>
          <Form.Item
            name="displayOrder"
            label="Order"
            rules={[{ required: true }]}
            extra="Classes are listed by this number (Nursery 0, LKG 1, …)."
          >
            <InputNumber min={0} max={999} style={{ width: '100%' }} />
          </Form.Item>
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>

      <Modal
        title={
          editingSection?.section
            ? `Edit ${editingSection.cls.name} – ${editingSection.section.name}`
            : `Add section to ${editingSection?.cls.name ?? ''}`
        }
        open={editingSection !== null}
        onCancel={() => setEditingSection(null)}
        onOk={() => sectionForm.submit()}
        okText="Save"
        confirmLoading={saveSection.isPending}
        destroyOnHidden
      >
        <Form
          form={sectionForm}
          layout="vertical"
          onFinish={(v) => saveSection.mutate({ ...v, classTeacherId: v.classTeacherId ?? null, capacity: v.capacity ?? null })}
        >
          <Form.Item
            name="name"
            label="Section name"
            rules={[{ required: true, whitespace: true }, { max: 10 }]}
            normalize={(v: string) => v?.toUpperCase()}
          >
            <Input placeholder="A" />
          </Form.Item>
          <Form.Item name="classTeacherId" label="Class teacher" extra="Only staff with the Teacher role are listed.">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Choose a teacher"
              options={teachers.map((t) => ({
                value: t.id,
                label: t.fullName,
                title: t.designation ?? undefined,
              }))}
              notFoundContent={<Tag>No teachers yet — add them under Users</Tag>}
            />
          </Form.Item>
          <Form.Item name="capacity" label="Capacity">
            <InputNumber min={1} max={200} style={{ width: '100%' }} />
          </Form.Item>
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
