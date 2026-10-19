import { useState } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { App, Button, Card, DatePicker, Flex, Input, Select, Table, Tag } from 'antd'
import { FilePdfOutlined } from '@ant-design/icons'
import type { Dayjs } from 'dayjs'
import { certificatesApi, type CertificateType, type IssuedCertificate } from '../../api/certificates'
import { errorMessage } from '../../api/client'
import { PageHeader } from '../../components/PageHeader'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate } from '../../utils/format'
import { openPdf } from '../../utils/openPdf'

/** The certificate register: every certificate issued, searchable, each re-downloadable. */
export function CertificatesPage() {
  const { message } = App.useApp()
  const [type, setType] = useState<CertificateType>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const params = {
    type,
    from: range?.[0]?.format(API_DATE_FORMAT),
    to: range?.[1]?.format(API_DATE_FORMAT),
    q: q.trim() || undefined,
    page,
    size: 20,
  }
  const list = useQuery({
    queryKey: ['certificates', params],
    queryFn: () => certificatesApi.list(params),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Certificates" subtitle="Issue new ones from a student's profile → Certificates." />
      <Card>
        <Flex gap={8} wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="Number, student or admission no."
            style={{ maxWidth: 280 }}
            onSearch={(v) => {
              setQ(v)
              setPage(0)
            }}
          />
          <Select
            allowClear
            placeholder="All types"
            style={{ minWidth: 170 }}
            value={type}
            onChange={(v) => {
              setType(v)
              setPage(0)
            }}
            options={[
              { value: 'BONAFIDE', label: 'Bonafide' },
              { value: 'TC', label: 'Transfer certificate' },
            ]}
          />
          <DatePicker.RangePicker
            format={DATE_FORMAT}
            value={range}
            onChange={(r) => {
              setRange(r)
              setPage(0)
            }}
          />
        </Flex>
        <Table<IssuedCertificate>
          rowKey="id"
          size="small"
          loading={list.isFetching}
          dataSource={list.data?.content ?? []}
          scroll={{ x: true }}
          locale={{ emptyText: list.isError ? errorMessage(list.error) : 'No certificates issued yet' }}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: list.data?.totalElements ?? 0,
            showSizeChanger: false,
            onChange: (p) => setPage(p - 1),
          }}
          columns={[
            { title: 'No.', dataIndex: 'serialNo' },
            { title: 'Issued', dataIndex: 'issuedOn', render: formatDate },
            {
              title: 'Student',
              key: 's',
              render: (_, c) => (
                <Link to={`/students/${c.studentId}`}>
                  {c.studentName} <span style={{ color: '#888' }}>({c.admissionNo})</span>
                </Link>
              ),
            },
            {
              title: 'Certificate',
              dataIndex: 'type',
              render: (t: CertificateType, c) => <Tag color={t === 'TC' ? 'orange' : 'blue'}>{c.title}</Tag>,
            },
            { title: 'Purpose', dataIndex: 'purpose', render: (v: string | null) => v ?? '—' },
            { title: 'By', dataIndex: 'issuedByName' },
            {
              key: 'pdf',
              align: 'right',
              render: (_, c) => (
                <Button
                  size="small"
                  icon={<FilePdfOutlined />}
                  aria-label={`Open ${c.serialNo}`}
                  onClick={() => openPdf(() => certificatesApi.pdf(c.id)).catch((e) => message.error(errorMessage(e)))}
                />
              ),
            },
          ]}
        />
      </Card>
    </>
  )
}
