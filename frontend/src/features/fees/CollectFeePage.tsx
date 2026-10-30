import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, App, Button, Card, Checkbox, Col, DatePicker, Descriptions, Divider, Empty, Flex, Input, InputNumber, Result, Row,
  Select, Space, Spin, Table, Tag, Typography, theme,
} from 'antd'
import { CreditCardOutlined, PrinterOutlined } from '@ant-design/icons'
import { isAxiosError } from 'axios'
import dayjs from 'dayjs'
import { errorMessage } from '../../api/client'
import { feesApi, type Due, type PaymentMode, type Receipt } from '../../api/fees'
import { studentsApi } from '../../api/students'
import { PageHeader } from '../../components/PageHeader'
import { API_DATE_FORMAT, DATE_FORMAT, formatDate, formatINR } from '../../utils/format'
import { PaymentCancelled, payWithRazorpay } from '../../utils/razorpay'
import { MODES, modeLabel } from './labels'
import { openReceiptPdf } from './receiptPdf'

const round2 = (n: number) => Math.round(n * 100) / 100

/** Search a student → tick dues (part payments allowed) → mode → Collect → print. */
export function CollectFeePage() {
  const { message } = App.useApp()
  const { token } = theme.useToken()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const studentId = Number(params.get('student')) || undefined
  const [search, setSearch] = useState('')
  /** Amount to pay per due id (only ticked dues are present). */
  const [paying, setPaying] = useState<Record<number, number>>({})
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [reference, setReference] = useState('')
  const [paidOn, setPaidOn] = useState(dayjs())
  const [done, setDone] = useState<Receipt | null>(null)

  const matches = useQuery({
    queryKey: ['fee-student-search', search],
    queryFn: () => studentsApi.search({ q: search, page: 0, size: 10, status: 'ACTIVE' }),
    enabled: search.trim().length >= 2,
  })
  const fees = useQuery({
    queryKey: ['student-fees', studentId],
    queryFn: () => feesApi.studentFees(studentId!),
    enabled: !!studentId,
  })

  const openDues = useMemo(() => (fees.data?.dues ?? []).filter((d) => d.balance > 0), [fees.data])
  const years = [...new Set(openDues.map((d) => d.academicYearId))]
  // One receipt per academic year: once a due is ticked, only that year stays selectable.
  const selectedYear = openDues.find((d) => paying[d.id] !== undefined)?.academicYearId
  const total = round2(Object.values(paying).reduce((a, b) => a + (b || 0), 0))

  const lines = () =>
    Object.entries(paying)
      .filter(([, amount]) => amount > 0)
      .map(([dueId, amount]) => ({ dueId: Number(dueId), amount }))

  const received = (receipt: Receipt) => {
    setDone(receipt)
    setPaying({})
    setReference('')
    queryClient.invalidateQueries({ queryKey: ['student-fees', studentId] })
    queryClient.invalidateQueries({ queryKey: ['fee-receipts'] })
    queryClient.invalidateQueries({ queryKey: ['fee-reports'] })
  }

  const collect = useMutation({
    mutationFn: () =>
      feesApi.collect({
        studentId: studentId!,
        paidOn: paidOn.format(API_DATE_FORMAT),
        paymentMode: mode,
        referenceNo: reference.trim() || undefined,
        lines: lines(),
      }),
    onSuccess: received,
    onError: (e) => message.error(errorMessage(e)),
  })

  // Online (Razorpay): the server fixes the order, the payment window takes the money,
  // the server verifies it and creates the receipt.
  const online = useQuery({ queryKey: ['fees-online-config'], queryFn: feesApi.onlineConfig, staleTime: Infinity })
  const payOnline = useMutation({
    mutationFn: async () => {
      const order = await feesApi.startOnline(studentId!, lines())
      const result = await payWithRazorpay(order, token.colorPrimary)
      return feesApi.completeOnline(order.id, result)
    },
    onSuccess: received,
    onError: (e) => {
      if (e instanceof PaymentCancelled) {
        message.info('Payment window closed. No receipt was made.')
      } else if (e instanceof Error && !isAxiosError(e)) {
        message.error(e.message)
      } else {
        message.error(errorMessage(e), 8)
      }
      queryClient.invalidateQueries({ queryKey: ['student-fees', studentId] })
    },
  })

  const pickStudent = (id: number) => {
    setParams({ student: String(id) })
    setPaying({})
    setDone(null)
  }
  const toggle = (d: Due, on: boolean) =>
    setPaying((p) => {
      const next = { ...p }
      if (on) next[d.id] = d.balance
      else delete next[d.id]
      return next
    })
  const print = (id: number) => openReceiptPdf(id).catch((e) => message.error(errorMessage(e)))

  if (done) {
    return (
      <Result
        status="success"
        title={`${formatINR(done.totalAmount)} received`}
        subTitle={`Receipt ${done.receiptNo} · ${done.studentName} · ${modeLabel(done.paymentMode)}`}
        extra={[
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={() => print(done.id)}>
            Print receipt
          </Button>,
          <Button key="same" onClick={() => setDone(null)}>
            Back to {done.studentName}
          </Button>,
          <Button key="next" onClick={() => { setParams({}); setDone(null); setSearch('') }}>
            Next student
          </Button>,
        ]}
      />
    )
  }

  return (
    <>
      <PageHeader title="Collect fee" />
      <Card style={{ marginBottom: 16 }}>
        <Select
          showSearch
          style={{ width: '100%', maxWidth: 480 }}
          size="large"
          placeholder="Search student by name, admission no. or parent's phone"
          filterOption={false}
          onSearch={setSearch}
          notFoundContent={matches.isFetching ? <Spin size="small" /> : search.length >= 2 ? 'No students found' : null}
          value={null as unknown as number}
          onChange={pickStudent}
          options={(matches.data?.content ?? []).map((s) => ({
            value: s.id,
            label: `${s.fullName} · ${s.className ? `${s.className} – ${s.sectionName}` : 'not enrolled'} · Adm. ${s.admissionNo}`,
          }))}
        />
      </Card>

      {!studentId ? (
        <Card>
          <Empty description="Search for a student to see what they owe." />
        </Card>
      ) : fees.error ? (
        <Card>
          <Result
            status="error"
            title="Could not load this student's fees"
            subTitle={errorMessage(fees.error)}
            extra={<Button onClick={() => fees.refetch()}>Try again</Button>}
          />
        </Card>
      ) : fees.isLoading || !fees.data ? (
        <Card loading />
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            <Card
              title={
                <Link to={`/students/${fees.data.studentId}`}>
                  {fees.data.fullName} <Typography.Text type="secondary">· {fees.data.classLabel}</Typography.Text>
                </Link>
              }
              extra={fees.data.guardianPhone && <a href={`tel:${fees.data.guardianPhone}`}>{fees.data.guardianPhone}</a>}
            >
              {openDues.length === 0 ? (
                <Empty description="Nothing due. All fees are paid." />
              ) : (
                <Table<Due>
                  rowKey="id"
                  size="small"
                  pagination={false}
                  scroll={{ x: true }}
                  dataSource={openDues}
                  columns={[
                    {
                      key: 'pick',
                      width: 40,
                      render: (_, d) => (
                        <Checkbox
                          checked={paying[d.id] !== undefined}
                          disabled={selectedYear !== undefined && selectedYear !== d.academicYearId}
                          onChange={(e) => toggle(d, e.target.checked)}
                        />
                      ),
                    },
                    {
                      title: 'Fee',
                      key: 'fee',
                      render: (_, d) => (
                        <>
                          {d.feeHeadName} – {d.installmentName}
                          {years.length > 1 && <Tag style={{ marginLeft: 6 }}>{d.academicYearName}</Tag>}
                          {d.overdue && <Tag color="red" style={{ marginLeft: 6 }}>Overdue</Tag>}
                        </>
                      ),
                    },
                    { title: 'Due date', dataIndex: 'dueDate', render: formatDate },
                    { title: 'Balance', dataIndex: 'balance', align: 'right', render: formatINR },
                    {
                      title: 'Paying now',
                      key: 'amount',
                      width: 150,
                      render: (_, d) =>
                        paying[d.id] === undefined ? null : (
                          <InputNumber
                            min={0.01}
                            max={d.balance}
                            step={100}
                            precision={2}
                            value={paying[d.id]}
                            onChange={(v) => setPaying((p) => ({ ...p, [d.id]: v ?? 0 }))}
                            style={{ width: '100%' }}
                          />
                        ),
                    },
                  ]}
                />
              )}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card title="Payment">
              <Descriptions column={1} size="small" style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Total fees">{formatINR(fees.data.totals.net)}</Descriptions.Item>
                <Descriptions.Item label="Paid so far">{formatINR(fees.data.totals.paid)}</Descriptions.Item>
                <Descriptions.Item label="Balance">
                  <b>{formatINR(fees.data.totals.balance)}</b>
                  {fees.data.totals.overdue > 0 && <Tag color="red" style={{ marginLeft: 6 }}>{formatINR(fees.data.totals.overdue)} overdue</Tag>}
                </Descriptions.Item>
              </Descriptions>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Select value={mode} onChange={setMode} options={MODES} style={{ width: '100%' }} />
                {mode !== 'CASH' && (
                  <Input
                    placeholder={mode === 'CHEQUE' ? 'Cheque number (required)' : 'Reference (UPI ref, transaction id)'}
                    value={reference}
                    maxLength={60}
                    onChange={(e) => setReference(e.target.value)}
                  />
                )}
                <DatePicker
                  allowClear={false}
                  format={DATE_FORMAT}
                  value={paidOn}
                  onChange={(d) => d && setPaidOn(d)}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                  style={{ width: '100%' }}
                />
                {mode === 'CHEQUE' && !reference.trim() && total > 0 && (
                  <Alert type="warning" showIcon title="Enter the cheque number" />
                )}
                <Flex justify="space-between" align="center" style={{ marginTop: 8 }}>
                  <Typography.Text>Collecting</Typography.Text>
                  <Typography.Title level={3} style={{ margin: 0 }}>
                    {formatINR(total)}
                  </Typography.Title>
                </Flex>
                <Button
                  type="primary"
                  size="large"
                  block
                  disabled={total <= 0 || (mode === 'CHEQUE' && !reference.trim()) || payOnline.isPending}
                  loading={collect.isPending}
                  onClick={() => collect.mutate()}
                >
                  Collect {total > 0 ? formatINR(total) : ''}
                </Button>
                {online.data?.enabled && (
                  <>
                    <Divider plain style={{ margin: '4px 0', fontSize: 12 }}>
                      or
                    </Divider>
                    <Button
                      size="large"
                      block
                      icon={<CreditCardOutlined />}
                      disabled={total <= 0 || collect.isPending}
                      loading={payOnline.isPending}
                      onClick={() => payOnline.mutate()}
                    >
                      Pay {total > 0 ? formatINR(total) : ''} online
                    </Button>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      UPI, card or netbanking through Razorpay. The receipt is made once the payment is confirmed.
                    </Typography.Text>
                  </>
                )}
              </Space>
            </Card>
          </Col>
        </Row>
      )}
    </>
  )
}
