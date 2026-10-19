import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Flex, Form, Input, InputNumber, Modal, Popconfirm, Space, Table, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { reportCardApi, type CoScholasticArea } from '../../api/exams'
import { useAuth } from '../../auth/useAuth'
import { useFormErrors } from '../../components/useFormErrors'
import { PRINCIPAL } from '../../layout/menu'

/** Exam setup → Co-scholastic: the areas graded A/B/C on report cards; link to the report card template. */
export function CoScholasticTab() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const { hasAnyRole } = useAuth()
  const [form] = Form.useForm<{ name: string; displayOrder?: number }>()
  const [editing, setEditing] = useState<CoScholasticArea | 'new' | null>(null)
  const { data = [], isLoading } = useQuery({ queryKey: ['co-scholastic-areas'], queryFn: reportCardApi.areas })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['co-scholastic-areas'] })
  const save = useMutation({
    mutationFn: (v: { name: string; displayOrder?: number }) => reportCardApi.saveArea(editing && editing !== 'new' ? editing.id : null, v),
    onSuccess: () => {
      invalidate()
      setEditing(null)
    },
    onError: (e) => showErrors(form, e),
  })
  const remove = useMutation({ mutationFn: reportCardApi.deleteArea, onSuccess: invalidate, onError: (e) => message.error(errorMessage(e)) })
  const open = (a: CoScholasticArea | 'new') => {
    setEditing(a)
    form.resetFields()
    form.setFieldsValue(a === 'new' ? { displayOrder: data.length + 1 } : a)
  }

  return (
    <>
      <Typography.Paragraph type="secondary">
        Areas graded outside academics (A, B or C), e.g. Art Education, Health &amp; Physical Education, Discipline. Class
        teachers enter the grades and a remark under <Link to="/exams/remarks">Exams → Remarks &amp; grades</Link>.
      </Typography.Paragraph>
      <Flex gap={8} wrap style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>
          Add area
        </Button>
        {hasAnyRole(PRINCIPAL) && (
          <Link to="/exams/template">
            <Button icon={<EditOutlined />}>Edit report card template</Button>
          </Link>
        )}
      </Flex>
      <Table<CoScholasticArea>
        rowKey="id"
        size="small"
        loading={isLoading}
        dataSource={data}
        pagination={false}
        locale={{ emptyText: 'No areas yet: report cards show academics only' }}
        columns={[
          { title: 'Order', dataIndex: 'displayOrder', width: 70 },
          { title: 'Area', dataIndex: 'name' },
          {
            key: 'a',
            align: 'right',
            render: (_, a) => (
              <Space>
                <Button size="small" onClick={() => open(a)}>
                  Edit
                </Button>
                <Popconfirm title={`Remove ${a.name}?`} description="Grades already given stop being printed." onConfirm={() => remove.mutate(a.id)}>
                  <Button size="small" danger icon={<DeleteOutlined />} aria-label="Remove" />
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={editing !== null}
        title={editing === 'new' ? 'Add area' : 'Edit area'}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
            <Input placeholder="e.g. Art Education" />
          </Form.Item>
          <Form.Item name="displayOrder" label="Order">
            <InputNumber min={0} max={999} />
          </Form.Item>
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
