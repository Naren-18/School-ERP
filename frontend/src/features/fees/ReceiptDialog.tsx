import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Descriptions, Input, Modal, Skeleton, Space, Table, Tag } from 'antd'
import { PrinterOutlined, StopOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { feesApi } from '../../api/fees'
import { useAuth } from '../../auth/useAuth'
import { formatDate, formatDateTime, formatINR } from '../../utils/format'
import { modeLabel } from './labels'
import { openReceiptPdf } from './receiptPdf'

/** Receipt details with Print and (principal/accountant) Cancel. */
export function ReceiptDialog({ receiptId, onClose }: { receiptId: number; onClose: () => void }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { hasAnyRole } = useAuth()
  const canCancel = hasAnyRole(['PRINCIPAL', 'ACCOUNTANT'])
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')
  const { data: r, isLoading, error } = useQuery({ queryKey: ['fee-receipt', receiptId], queryFn: () => feesApi.receipt(receiptId) })

  const cancel = useMutation({
    mutationFn: () => feesApi.cancel(receiptId, reason.trim()),
    onSuccess: (updated) => {
      queryClient.setQueryData(['fee-receipt', receiptId], updated)
      queryClient.invalidateQueries({ queryKey: ['fee-receipts'] })
      queryClient.invalidateQueries({ queryKey: ['student-fees'] })
      queryClient.invalidateQueries({ queryKey: ['fee-reports'] })
      setCancelling(false)
      message.success(`Receipt ${updated.receiptNo} cancelled; its dues are payable again`)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Modal
      open
      width={640}
      title={r ? `Receipt ${r.receiptNo}` : 'Receipt'}
      onCancel={onClose}
      footer={
        r && (
          <Space wrap>
            {canCancel && !r.cancelled && !cancelling && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelling(true)}>
                Cancel receipt
              </Button>
            )}
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              onClick={() => openReceiptPdf(r.id).catch((e) => message.error(errorMessage(e)))}
            >
              Print / PDF
            </Button>
          </Space>
        )
      }
    >
      {error ? (
        <Alert type="error" showIcon title="Could not load this receipt" description={errorMessage(error)} />
      ) : isLoading || !r ? (
        <Skeleton active />
      ) : (
        <>
          {r.cancelled && (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 12 }}
              title={`Cancelled ${formatDateTime(r.cancelledAt)} by ${r.cancelledByName}`}
              description={r.cancelReason}
            />
          )}
          <Descriptions size="small" column={{ xs: 1, sm: 2 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Student">
              {r.studentName} (Adm. {r.admissionNo})
            </Descriptions.Item>
            <Descriptions.Item label="Class">
              {r.classLabel} · {r.academicYearName}
            </Descriptions.Item>
            <Descriptions.Item label="Paid on">{formatDate(r.paidOn)}</Descriptions.Item>
            <Descriptions.Item label="Mode">
              {modeLabel(r.paymentMode)}
              {r.referenceNo && ` · ${r.referenceNo}`}
            </Descriptions.Item>
            <Descriptions.Item label="Collected by">{r.collectedByName}</Descriptions.Item>
            <Descriptions.Item label="Total">
              <b>{formatINR(r.totalAmount)}</b> {r.cancelled && <Tag color="red">Cancelled</Tag>}
            </Descriptions.Item>
          </Descriptions>
          <Table
            rowKey="dueId"
            size="small"
            pagination={false}
            dataSource={r.lines}
            columns={[
              { title: 'Particulars', key: 'p', render: (_, l) => `${l.feeHeadName} – ${l.installmentName}` },
              { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatINR },
            ]}
          />
          <p style={{ marginTop: 8, fontStyle: 'italic' }}>{r.amountInWords}</p>
          {cancelling && (
            <Space.Compact style={{ width: '100%', marginTop: 8 }}>
              <Input
                autoFocus
                placeholder="Reason for cancelling (required)"
                maxLength={200}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
              <Button danger type="primary" disabled={!reason.trim()} loading={cancel.isPending} onClick={() => cancel.mutate()}>
                Confirm cancel
              </Button>
            </Space.Compact>
          )}
        </>
      )}
    </Modal>
  )
}
