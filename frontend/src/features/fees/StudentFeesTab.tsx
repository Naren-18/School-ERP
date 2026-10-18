import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Descriptions, Flex, Form, Input, InputNumber, Modal, Result, Skeleton, Table, Tag, Typography } from 'antd'
import { errorMessage } from '../../api/client'
import { feesApi, type Due, type ReceiptSummary } from '../../api/fees'
import { formatDate, formatINR } from '../../utils/format'
import { modeLabel } from './labels'
import { ReceiptDialog } from './ReceiptDialog'

/** Student profile → Fees: the ledger (every year), concessions, and receipts. */
export function StudentFeesTab({ studentId }: { studentId: number }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<{ concession: number; reason?: string }>()
  const [concessionFor, setConcessionFor] = useState<Due | null>(null)
  const [receipt, setReceipt] = useState<number>()
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['student-fees', studentId],
    queryFn: () => feesApi.studentFees(studentId),
  })

  const concession = useMutation({
    mutationFn: (v: { concession: number; reason?: string }) => feesApi.setConcession(concessionFor!.id, v.concession, v.reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['student-fees', studentId] })
      setConcessionFor(null)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  if (error) {
    return (
      <Result
        status="error"
        title="Could not load fees"
        subTitle={errorMessage(error)}
        extra={<Button onClick={() => refetch()}>Try again</Button>}
      />
    )
  }
  if (isLoading || !data) return <Skeleton active />

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Descriptions size="small" column={{ xs: 2, md: 4 }} style={{ flex: 1 }}>
          <Descriptions.Item label="Total">{formatINR(data.totals.net)}</Descriptions.Item>
          <Descriptions.Item label="Paid">{formatINR(data.totals.paid)}</Descriptions.Item>
          <Descriptions.Item label="Balance">
            <b>{formatINR(data.totals.balance)}</b>
          </Descriptions.Item>
          <Descriptions.Item label="Overdue">
            {data.totals.overdue > 0 ? <b style={{ color: '#b91c1c' }}>{formatINR(data.totals.overdue)}</b> : '—'}
          </Descriptions.Item>
        </Descriptions>
        {data.totals.balance > 0 && (
          <Link to={`/fees/collect?student=${studentId}`}>
            <Button type="primary">Collect fee</Button>
          </Link>
        )}
      </Flex>

      <Table<Due>
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: true }}
        dataSource={data.dues}
        locale={{ emptyText: 'No fee plan for this class yet' }}
        columns={[
          {
            title: 'Fee',
            key: 'fee',
            render: (_, d) => (
              <>
                {d.feeHeadName} – {d.installmentName} <Typography.Text type="secondary">({d.academicYearName})</Typography.Text>
                {d.overdue && <Tag color="red" style={{ marginLeft: 6 }}>Overdue</Tag>}
              </>
            ),
          },
          { title: 'Due', dataIndex: 'dueDate', render: formatDate },
          { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatINR },
          {
            title: 'Concession',
            key: 'c',
            align: 'right',
            render: (_, d) => (
              <a
                onClick={() => {
                  setConcessionFor(d)
                  form.setFieldsValue({ concession: d.concession, reason: d.concessionReason ?? undefined })
                }}
                title={d.concessionReason ?? 'Give a concession'}
              >
                {d.concession > 0 ? formatINR(d.concession) : 'Add'}
              </a>
            ),
          },
          { title: 'Paid', dataIndex: 'paid', align: 'right', render: formatINR },
          { title: 'Balance', dataIndex: 'balance', align: 'right', render: (v: number) => (v > 0 ? <b>{formatINR(v)}</b> : <Tag color="green">Paid</Tag>) },
        ]}
      />

      <div>
        <Typography.Title level={5}>Receipts</Typography.Title>
        <Table<ReceiptSummary>
          rowKey="id"
          size="small"
          pagination={false}
          scroll={{ x: true }}
          dataSource={data.receipts}
          locale={{ emptyText: 'No payments yet' }}
          onRow={(r) => ({ onClick: () => setReceipt(r.id), style: { cursor: 'pointer' } })}
          columns={[
            { title: 'Receipt', dataIndex: 'receiptNo', render: (no: string, r) => <>{no} {r.cancelled && <Tag color="red">Cancelled</Tag>}</> },
            { title: 'Date', dataIndex: 'paidOn', render: formatDate },
            { title: 'Mode', dataIndex: 'paymentMode', render: modeLabel },
            { title: 'Amount', dataIndex: 'totalAmount', align: 'right', render: formatINR },
          ]}
        />
      </div>

      <Modal
        open={concessionFor !== null}
        title={concessionFor ? `Concession: ${concessionFor.feeHeadName} – ${concessionFor.installmentName}` : ''}
        onCancel={() => setConcessionFor(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={concession.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => concession.mutate(v)}>
          <Form.Item
            name="concession"
            label={`Concession (₹), at most ${concessionFor ? formatINR(concessionFor.amount - concessionFor.paid) : ''}`}
            rules={[{ required: true }]}
          >
            <InputNumber min={0} max={concessionFor ? concessionFor.amount - concessionFor.paid : undefined} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="Reason" rules={[{ max: 100 }]} extra="Required when giving a concession, e.g. Sibling, Staff ward, Scholarship.">
            <Input />
          </Form.Item>
          <button type="submit" hidden />
        </Form>
      </Modal>
      {receipt && <ReceiptDialog receiptId={receipt} onClose={() => setReceipt(undefined)} />}
    </Flex>
  )
}
