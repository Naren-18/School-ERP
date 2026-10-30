import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Flex, Input, Select, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import {
  MESSAGE_TYPES, messageTypeLabel, notificationsApi, type LogRow, type MessageStatus, type MessageType,
} from '../../api/notifications'
import { PageHeader } from '../../components/PageHeader'
import { NotConnectedNote } from '../../components/SendReportSummary'
import { API_DATE_FORMAT, DATE_FORMAT, formatDateTime } from '../../utils/format'

const PAGE_SIZE = 20

/** Every WhatsApp message tried, newest first: proof that a parent was told, and failures to follow up. */
export function MessageLogPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(30, 'day'), dayjs()])
  const [type, setType] = useState<MessageType>()
  const [status, setStatus] = useState<MessageStatus>()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const from = range[0].format(API_DATE_FORMAT)
  const to = range[1].format(API_DATE_FORMAT)

  const connection = useQuery({ queryKey: ['notifications-status'], queryFn: notificationsApi.status })
  const log = useQuery({
    queryKey: ['notifications', from, to, type, status, q, page],
    queryFn: () => notificationsApi.log({ from, to, type, status, q: q.trim() || undefined, page, size: PAGE_SIZE }),
    placeholderData: (previous) => previous,
  })
  const reset = () => setPage(0)

  return (
    <>
      <PageHeader title="WhatsApp messages" subtitle="Messages to parents: absences, fees, payments and results" />
      {connection.data && !connection.data.connected && (
        <div style={{ marginBottom: 16 }}>
          <NotConnectedNote />
        </div>
      )}
      <Card>
        <Flex gap={8} wrap style={{ marginBottom: 16 }}>
          <DatePicker.RangePicker
            allowClear={false}
            format={DATE_FORMAT}
            value={range}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(v) => {
              if (v?.[0] && v[1]) {
                setRange([v[0], v[1]])
                reset()
              }
            }}
          />
          <Select
            allowClear
            placeholder="All messages"
            style={{ minWidth: 170 }}
            value={type}
            onChange={(v) => {
              setType(v)
              reset()
            }}
            options={MESSAGE_TYPES}
          />
          <Select
            allowClear
            placeholder="Sent or failed"
            style={{ minWidth: 140 }}
            value={status}
            onChange={(v) => {
              setStatus(v)
              reset()
            }}
            options={[
              { value: 'SENT', label: 'Sent' },
              { value: 'FAILED', label: 'Failed' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="Student or phone"
            style={{ maxWidth: 240 }}
            onSearch={(v) => {
              setQ(v)
              reset()
            }}
          />
        </Flex>
        <Table<LogRow>
          rowKey="id"
          size="small"
          loading={log.isFetching}
          dataSource={log.data?.content ?? []}
          scroll={{ x: true }}
          locale={{ emptyText: 'No messages in this period' }}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total: log.data?.totalElements ?? 0,
            showSizeChanger: false,
            hideOnSinglePage: true,
            onChange: (p) => setPage(p - 1),
          }}
          columns={[
            { title: 'When', dataIndex: 'createdAt', render: formatDateTime, width: 170 },
            { title: 'Message', dataIndex: 'type', render: messageTypeLabel },
            { title: 'Student', key: 's', render: (_, r) => <Link to={`/students/${r.studentId}`}>{r.studentName}</Link> },
            { title: 'Phone', dataIndex: 'phone' },
            {
              title: 'Status',
              key: 'st',
              render: (_, r) =>
                r.status === 'SENT' ? (
                  <Tag color="green">Sent</Tag>
                ) : (
                  <Flex vertical>
                    <span>
                      <Tag color="red">Failed</Tag>
                    </span>
                    {r.error && (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {r.error}
                      </Typography.Text>
                    )}
                  </Flex>
                ),
            },
            { title: 'By', dataIndex: 'sentByName', render: (v: string | null) => v ?? 'Automatic' },
          ]}
        />
      </Card>
    </>
  )
}
