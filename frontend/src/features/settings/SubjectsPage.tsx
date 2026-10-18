import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { academicApi, type Subject, type SubjectInput, type SubjectType } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { keys, useSubjects } from './queries'
import { SubjectTypeTag } from './SubjectTypeTag'
import { SUBJECT_TYPES } from './subjectTypes'

export function SubjectsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<SubjectInput>()
  const [editing, setEditing] = useState<Subject | 'new' | null>(null)
  const { data: subjects = [], isLoading } = useSubjects()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: keys.subjects })
  const save = useMutation({
    mutationFn: (v: SubjectInput) =>
      editing && editing !== 'new' ? academicApi.updateSubject(editing.id, v) : academicApi.createSubject(v),
    onSuccess: () => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['class-subjects'] })
      setEditing(null)
    },
    onError: (e) => showErrors(form, e),
  })
  const remove = useMutation({
    mutationFn: academicApi.deleteSubject,
    onSuccess: invalidate,
    onError: (e) => message.error(errorMessage(e)),
  })

  const open = (s: Subject | 'new') => {
    setEditing(s)
    form.resetFields()
    form.setFieldsValue(s === 'new' ? { type: 'CORE' } : s)
  }

  return (
    <>
      <PageHeader
        title="Subjects"
        subtitle="The school's subject list. Choose which classes study each one under Class subjects."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>
            Add subject
          </Button>
        }
      />
      <Card>
        <Table<Subject>
          rowKey="id"
          loading={isLoading}
          dataSource={subjects}
          pagination={false}
          scroll={{ x: true }}
          columns={[
            { title: 'Subject', dataIndex: 'name' },
            { title: 'Code', dataIndex: 'code', render: (c: string | null) => c ?? '—' },
            { title: 'Type', dataIndex: 'type', render: (t: SubjectType) => <SubjectTypeTag type={t} /> },
            {
              title: '',
              key: 'actions',
              align: 'right',
              render: (_, s) => (
                <Space>
                  <Button size="small" onClick={() => open(s)}>
                    Edit
                  </Button>
                  <Popconfirm title={`Delete ${s.name}?`} onConfirm={() => remove.mutate(s.id)}>
                    <Button size="small" danger>
                      Delete
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal
        title={editing === 'new' ? 'Add subject' : 'Edit subject'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
            <Input placeholder="e.g. Mathematics" />
          </Form.Item>
          <Form.Item name="code" label="Code" rules={[{ max: 15 }]} normalize={(v: string) => v?.toUpperCase()}>
            <Input placeholder="e.g. MAT" />
          </Form.Item>
          <Form.Item name="type" label="Type" rules={[{ required: true }]}>
            <Select options={SUBJECT_TYPES} />
          </Form.Item>
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
