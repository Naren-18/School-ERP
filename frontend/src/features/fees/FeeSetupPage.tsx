import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App, Button, Card, Col, DatePicker, Empty, Flex, Form, Input, InputNumber, List, Modal, Popconfirm, Row, Select, Space,
  Switch, Table, Tag, Typography,
} from 'antd'
import { CopyOutlined, PlusOutlined, ThunderboltOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { errorMessage } from '../../api/client'
import { feesApi, type Installment } from '../../api/fees'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate, formatINR } from '../../utils/format'
import { useClasses, useYears } from '../settings/queries'
import { YearSelect } from '../settings/YearSelect'

interface InstallmentForm {
  feeHeadId: number
  name: string
  amount: number
  dueDate: Dayjs
}

/** Fee heads (left) and the selected class's installment plan for a year (right). */
export function FeeSetupPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<InstallmentForm>()
  const [yearId, setYearId] = useState<number>()
  const [chosenClass, setChosenClass] = useState<number>()
  const [editing, setEditing] = useState<Installment | 'new' | null>(null)
  const [newHead, setNewHead] = useState('')
  const { data: years = [] } = useYears()
  const { data: classes = [] } = useClasses()
  const classId = chosenClass ?? classes[0]?.id
  const year = years.find((y) => y.id === yearId)

  const heads = useQuery({ queryKey: ['fee-heads'], queryFn: feesApi.heads })
  const plan = useQuery({
    queryKey: ['fee-plan', yearId, classId],
    queryFn: () => feesApi.plan(yearId!, classId),
    enabled: !!yearId && !!classId,
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['fee-plan'] })
    queryClient.invalidateQueries({ queryKey: ['student-fees'] })
  }
  const onError = (e: unknown) => message.error(errorMessage(e))

  const addHead = useMutation({
    mutationFn: () => feesApi.createHead(newHead.trim()),
    onSuccess: () => {
      setNewHead('')
      queryClient.invalidateQueries({ queryKey: ['fee-heads'] })
    },
    onError,
  })
  const toggleHead = useMutation({
    mutationFn: (h: { id: number; name: string; active: boolean }) => feesApi.updateHead(h.id, { name: h.name, active: !h.active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fee-heads'] }),
    onError,
  })
  const save = useMutation({
    mutationFn: (v: InstallmentForm) => {
      const body = { academicYearId: yearId!, classId: classId!, ...v, dueDate: v.dueDate.format(API_DATE_FORMAT) }
      return editing && editing !== 'new' ? feesApi.updateInstallment(editing.id, body) : feesApi.createInstallment(body)
    },
    onSuccess: () => {
      refresh()
      setEditing(null)
      message.success('Saved. New students get it automatically; use "Generate dues" for students already enrolled.')
    },
    onError: (e) => showErrors(form, e),
  })
  const remove = useMutation({ mutationFn: feesApi.deleteInstallment, onSuccess: refresh, onError })
  const generate = useMutation({
    mutationFn: () => feesApi.generateDues(yearId!, classId),
    onSuccess: (r) => {
      refresh()
      message.success(r.created === 0 ? 'Everyone already has these dues' : `${r.created} dues created`)
    },
    onError,
  })
  const copy = useMutation({
    mutationFn: (fromYear: number) => feesApi.copyPlan(fromYear, yearId!),
    onSuccess: (r) => {
      refresh()
      message.success(`${r.copied} installments copied${r.skipped ? `, ${r.skipped} already existed` : ''}`)
    },
    onError,
  })

  const open = (i: Installment | 'new') => {
    setEditing(i)
    form.resetFields()
    if (i !== 'new') form.setFieldsValue({ ...i, dueDate: dayjs(i.dueDate) })
  }
  const askCopy = () => {
    const others = years.filter((y) => y.id !== yearId)
    let from = others[0]?.id
    modal.confirm({
      title: `Copy every class's fee plan into ${year?.name}`,
      content: (
        <Select
          style={{ width: '100%', marginTop: 8 }}
          defaultValue={from}
          onChange={(v) => (from = v)}
          options={others.map((y) => ({ value: y.id, label: `From ${y.name}` }))}
        />
      ),
      okText: 'Copy',
      onOk: () => from && copy.mutateAsync(from),
    })
  }

  const total = (plan.data ?? []).reduce((s, i) => s + i.amount, 0)

  return (
    <>
      <PageHeader
        title="Fee setup"
        extra={
          <>
            <YearSelect value={yearId} onChange={setYearId} />
            <Button icon={<CopyOutlined />} disabled={!yearId || years.length < 2} onClick={askCopy}>
              Copy from another year
            </Button>
          </>
        }
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={7}>
          <Card title="Fee heads" size="small">
            <Space.Compact style={{ width: '100%', marginBottom: 8 }}>
              <Input placeholder="e.g. Transport" value={newHead} maxLength={60} onChange={(e) => setNewHead(e.target.value)} onPressEnter={() => newHead.trim() && addHead.mutate()} />
              <Button icon={<PlusOutlined />} disabled={!newHead.trim()} loading={addHead.isPending} onClick={() => addHead.mutate()} />
            </Space.Compact>
            <List
              size="small"
              dataSource={heads.data ?? []}
              locale={{ emptyText: 'Add Tuition, Transport, Books…' }}
              renderItem={(h) => (
                <List.Item extra={<Switch size="small" checked={h.active} onChange={() => toggleHead.mutate(h)} />}>
                  <Typography.Text type={h.active ? undefined : 'secondary'}>{h.name}</Typography.Text>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={17}>
          <Card
            size="small"
            title={
              <Select
                value={classId}
                onChange={setChosenClass}
                style={{ minWidth: 160 }}
                options={classes.map((c) => ({ value: c.id, label: c.name }))}
                placeholder="Class"
              />
            }
            extra={
              <Space wrap>
                <Button icon={<ThunderboltOutlined />} disabled={!plan.data?.length} loading={generate.isPending} onClick={() => generate.mutate()}>
                  Generate dues
                </Button>
                <Button type="primary" icon={<PlusOutlined />} disabled={!yearId || !classId} onClick={() => open('new')}>
                  Add installment
                </Button>
              </Space>
            }
          >
            {!yearId || !classId ? (
              <Empty description="Create an academic year and classes first." />
            ) : (
              <Table<Installment>
                rowKey="id"
                size="small"
                loading={plan.isLoading}
                dataSource={plan.data ?? []}
                pagination={false}
                scroll={{ x: true }}
                locale={{ emptyText: 'No installments yet for this class' }}
                summary={() =>
                  plan.data?.length ? (
                    <Table.Summary.Row>
                      <Table.Summary.Cell index={0} colSpan={3}>
                        <b>Total per student</b>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={1} align="right">
                        <b>{formatINR(total)}</b>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={2} colSpan={2} />
                    </Table.Summary.Row>
                  ) : null
                }
                columns={[
                  { title: 'Fee head', dataIndex: 'feeHeadName' },
                  { title: 'Installment', dataIndex: 'name' },
                  { title: 'Due date', dataIndex: 'dueDate', render: formatDate },
                  { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatINR },
                  { title: 'Students', dataIndex: 'students', render: (n: number) => <Tag>{n}</Tag> },
                  {
                    key: 'actions',
                    align: 'right',
                    render: (_, i) => (
                      <Space>
                        <Button size="small" onClick={() => open(i)}>
                          Edit
                        </Button>
                        <Popconfirm title="Delete this installment and its unpaid dues?" onConfirm={() => remove.mutate(i.id)}>
                          <Button size="small" danger>
                            Delete
                          </Button>
                        </Popconfirm>
                      </Space>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title={editing === 'new' ? 'Add installment' : 'Edit installment'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="feeHeadId" label="Fee head" rules={[{ required: true }]}>
            <Select
              disabled={editing !== 'new'}
              options={(heads.data ?? []).filter((h) => h.active || (editing !== 'new' && editing?.feeHeadId === h.id)).map((h) => ({ value: h.id, label: h.name }))}
            />
          </Form.Item>
          <Form.Item name="name" label="Installment name" rules={[{ required: true, whitespace: true }, { max: 40 }]}>
            <Input placeholder="e.g. Term 1, June, Annual" />
          </Form.Item>
          <Flex gap={12} wrap>
            <Form.Item name="amount" label="Amount (₹)" rules={[{ required: true }]} style={{ flex: '1 1 160px' }}>
              <InputNumber min={0} max={99999999} precision={2} step={500} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="dueDate" label="Due date" rules={[{ required: true }]} style={{ flex: '1 1 160px' }}>
              <DatePicker
                format={DATE_FORMAT}
                style={{ width: '100%' }}
                disabledDate={(d) => !!year && (d.isBefore(dayjs(year.startDate), 'day') || d.isAfter(dayjs(year.endDate), 'day'))}
              />
            </Form.Item>
          </Flex>
          {editing !== 'new' && editing && editing.students > 0 && (
            <Typography.Paragraph type="secondary">
              Changing the amount updates all {editing.students} students' dues.
            </Typography.Paragraph>
          )}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
