import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Flex, Input, Select, Table, Tag } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { feesApi, type ReceiptSummary } from '../../api/fees'
import { PageHeader } from '../../components/PageHeader'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate, formatINR } from '../../utils/format'
import { modeLabel } from './labels'
import { ReceiptDialog } from './ReceiptDialog'

/** The receipt book: search by number, filter by dates and status; open to print or cancel. */
export function ReceiptsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(30, 'day'), dayjs()])
  const [q, setQ] = useState('')
  const [status, setStatus] = useState<'all' | 'valid' | 'cancelled'>('all')
  const [page, setPage] = useState(1)
  const [open, setOpen] = useState<number>()

  const params = {
    from: range[0].format(API_DATE_FORMAT),
    to: range[1].format(API_DATE_FORMAT),
    q: q || undefined,
    cancelled: status === 'all' ? undefined : status === 'cancelled',
    page: page - 1,
    size: 20,
  }
  const { data, isFetching } = useQuery({
    queryKey: ['fee-receipts', params],
    queryFn: () => feesApi.receipts(params),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Receipts" />
      <Card>
        <Flex gap={8} wrap style={{ marginBottom: 16 }}>
          <DatePicker.RangePicker
            allowClear={false}
            format={DATE_FORMAT}
            value={range}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(v) => {
              if (v?.[0] && v[1]) setRange([v[0], v[1]])
              setPage(1)
            }}
          />
          <Input.Search
            allowClear
            placeholder="Receipt number"
            style={{ maxWidth: 220 }}
            onSearch={(v) => {
              setQ(v.trim())
              setPage(1)
            }}
          />
          <Select
            value={status}
            style={{ minWidth: 130 }}
            onChange={(v) => {
              setStatus(v)
              setPage(1)
            }}
            options={[
              { value: 'all', label: 'All' },
              { value: 'valid', label: 'Valid' },
              { value: 'cancelled', label: 'Cancelled' },
            ]}
          />
        </Flex>
        <Table<ReceiptSummary>
          rowKey="id"
          loading={isFetching}
          dataSource={data?.content ?? []}
          scroll={{ x: true }}
          onRow={(r) => ({ onClick: () => setOpen(r.id), style: { cursor: 'pointer' } })}
          pagination={{ current: page, pageSize: 20, total: data?.totalElements ?? 0, onChange: setPage, showSizeChanger: false }}
          columns={[
            {
              title: 'Receipt',
              dataIndex: 'receiptNo',
              render: (no: string, r) => (
                <>
                  {no} {r.cancelled && <Tag color="red">Cancelled</Tag>}
                </>
              ),
            },
            { title: 'Date', dataIndex: 'paidOn', render: formatDate },
            { title: 'Student', key: 's', render: (_, r) => `${r.studentName} (${r.classLabel})` },
            { title: 'Mode', dataIndex: 'paymentMode', render: modeLabel },
            {
              title: 'Amount',
              dataIndex: 'totalAmount',
              align: 'right',
              render: (v: number, r) => <span style={{ textDecoration: r.cancelled ? 'line-through' : undefined }}>{formatINR(v)}</span>,
            },
          ]}
        />
      </Card>
      {open && <ReceiptDialog receiptId={open} onClose={() => setOpen(undefined)} />}
    </>
  )
}
