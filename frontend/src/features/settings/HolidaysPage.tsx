import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Form, Input, Modal, Popconfirm, Space, Table } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { academicApi, type Holiday } from '../../api/academic'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { API_DATE_FORMAT, DATE_FORMAT } from '../../utils/format'
import { keys, useYears } from './queries'
import { YearSelect } from './YearSelect'

interface FormValues {
  date: Dayjs
  description: string
}

export function HolidaysPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<FormValues>()
  const [yearId, setYearId] = useState<number>()
  const [editing, setEditing] = useState<Holiday | 'new' | null>(null)
  const year = useYears().data?.find((y) => y.id === yearId)

  const { data: holidays = [], isLoading } = useQuery({
    queryKey: keys.holidays(yearId),
    queryFn: () => academicApi.holidays(yearId),
    enabled: !!yearId,
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: keys.holidays(yearId) })

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body = { academicYearId: yearId!, date: v.date.format(API_DATE_FORMAT), description: v.description }
      return editing && editing !== 'new' ? academicApi.updateHoliday(editing.id, body) : academicApi.createHoliday(body)
    },
    onSuccess: () => {
      invalidate()
      setEditing(null)
      message.success('Holiday saved')
    },
    onError: (e) => showErrors(form, e),
  })
  const remove = useMutation({
    mutationFn: academicApi.deleteHoliday,
    onSuccess: invalidate,
    onError: (e) => message.error(errorMessage(e)),
  })

  const open = (h: Holiday | 'new') => {
    setEditing(h)
    form.resetFields()
    if (h !== 'new') form.setFieldsValue({ date: dayjs(h.date), description: h.description })
  }

  return (
    <>
      <PageHeader
        title="Holidays"
        subtitle="Attendance cannot be marked on holidays or weekly off days."
        extra={
          <>
            <YearSelect value={yearId} onChange={setYearId} />
            <Button type="primary" icon={<PlusOutlined />} disabled={!yearId} onClick={() => open('new')}>
              Add holiday
            </Button>
          </>
        }
      />
      <Card>
        <Table<Holiday>
          rowKey="id"
          loading={isLoading}
          dataSource={holidays}
          pagination={false}
          scroll={{ x: true }}
          locale={{ emptyText: yearId ? 'No holidays added yet' : 'Create an academic year first' }}
          columns={[
            {
              title: 'Date',
              dataIndex: 'date',
              render: (d: string) => dayjs(d).format(`${DATE_FORMAT} (ddd)`),
            },
            { title: 'Description', dataIndex: 'description' },
            {
              title: '',
              key: 'actions',
              align: 'right',
              render: (_, h) => (
                <Space>
                  <Button size="small" onClick={() => open(h)}>
                    Edit
                  </Button>
                  <Popconfirm title="Delete this holiday?" onConfirm={() => remove.mutate(h.id)}>
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
        title={editing === 'new' ? 'Add holiday' : 'Edit holiday'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="date" label="Date" rules={[{ required: true, message: 'Choose a date' }]}>
            <DatePicker
              format={DATE_FORMAT}
              style={{ width: '100%' }}
              disabledDate={(d) =>
                !!year && (d.isBefore(dayjs(year.startDate), 'day') || d.isAfter(dayjs(year.endDate), 'day'))
              }
            />
          </Form.Item>
          <Form.Item
            name="description"
            label="Description"
            rules={[{ required: true, whitespace: true }, { max: 100 }]}
          >
            <Input placeholder="e.g. Sankranti" />
          </Form.Item>
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
