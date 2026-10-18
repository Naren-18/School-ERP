import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Form, Input, Modal, Popconfirm, Space, Table, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { academicApi, type AcademicYear } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate } from '../../utils/format'
import { keys, useYears } from './queries'

interface FormValues {
  name: string
  range: [Dayjs, Dayjs]
}

export function AcademicYearsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<FormValues>()
  const [editing, setEditing] = useState<AcademicYear | 'new' | null>(null)
  const { data: years = [], isLoading } = useYears()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: keys.years })
  const onError = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: ({ name, range }: FormValues) => {
      const body = { name, startDate: range[0].format(API_DATE_FORMAT), endDate: range[1].format(API_DATE_FORMAT) }
      return editing && editing !== 'new' ? academicApi.updateYear(editing.id, body) : academicApi.createYear(body)
    },
    onSuccess: () => {
      invalidate()
      setEditing(null)
      message.success('Academic year saved')
    },
    onError: (e) => showErrors(form, e),
  })
  const makeCurrent = useMutation({
    mutationFn: academicApi.makeCurrent,
    onSuccess: (year) => {
      queryClient.invalidateQueries()
      message.success(`${year.name} is now the current year`)
    },
    onError,
  })
  const remove = useMutation({ mutationFn: academicApi.deleteYear, onSuccess: invalidate, onError })

  const open = (year: AcademicYear | 'new') => {
    setEditing(year)
    form.resetFields()
    if (year !== 'new') {
      form.setFieldsValue({ name: year.name, range: [dayjs(year.startDate), dayjs(year.endDate)] })
    }
  }

  return (
    <>
      <PageHeader
        title="Academic years"
        subtitle="Exactly one year is current. Sections, attendance and fees use the current year by default."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>
            Add year
          </Button>
        }
      />
      <Card>
        <Table<AcademicYear>
          rowKey="id"
          loading={isLoading}
          dataSource={years}
          pagination={false}
          scroll={{ x: true }}
          columns={[
            {
              title: 'Year',
              dataIndex: 'name',
              render: (name: string, y) => (
                <>
                  {name} {y.current && <Tag color="green">Current</Tag>}
                </>
              ),
            },
            { title: 'Starts', dataIndex: 'startDate', render: formatDate },
            { title: 'Ends', dataIndex: 'endDate', render: formatDate },
            {
              title: '',
              key: 'actions',
              align: 'right',
              render: (_, y) => (
                <Space wrap>
                  <Button size="small" onClick={() => open(y)}>
                    Edit
                  </Button>
                  {!y.current && (
                    <>
                      <Popconfirm title={`Make ${y.name} the current year?`} onConfirm={() => makeCurrent.mutate(y.id)}>
                        <Button size="small">Make current</Button>
                      </Popconfirm>
                      <Popconfirm title={`Delete ${y.name}?`} onConfirm={() => remove.mutate(y.id)}>
                        <Button size="small" danger>
                          Delete
                        </Button>
                      </Popconfirm>
                    </>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={editing === 'new' ? 'Add academic year' : 'Edit academic year'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }, { max: 20 }]} extra="e.g. 2026-27">
            <Input />
          </Form.Item>
          <Form.Item
            name="range"
            label="Start and end dates"
            rules={[{ required: true, message: 'Choose the start and end dates' }]}
          >
            <DatePicker.RangePicker format={DATE_FORMAT} style={{ width: '100%' }} />
          </Form.Item>
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
