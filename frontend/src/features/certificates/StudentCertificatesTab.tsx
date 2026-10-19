import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { App, Button, Flex, Table } from 'antd'
import { FilePdfOutlined, PlusOutlined } from '@ant-design/icons'
import { certificatesApi, type IssuedCertificate } from '../../api/certificates'
import { errorMessage } from '../../api/client'
import { formatDate } from '../../utils/format'
import { openPdf } from '../../utils/openPdf'
import { IssueCertificateDialog } from './IssueCertificateDialog'

/** Student profile → Certificates: what was issued, and issuing a new one. */
export function StudentCertificatesTab({ studentId, studentName, status }: { studentId: number; studentName: string; status: string }) {
  const { message } = App.useApp()
  const [issuing, setIssuing] = useState(false)
  const { data = [], isLoading } = useQuery({
    queryKey: ['certificates', 'student', studentId],
    queryFn: () => certificatesApi.forStudent(studentId),
  })
  const canIssue = status !== 'TC_ISSUED'
  return (
    <Flex vertical gap={12}>
      <div>
        <Button type="primary" icon={<PlusOutlined />} disabled={!canIssue} onClick={() => setIssuing(true)}>
          Issue certificate
        </Button>
      </div>
      <Table<IssuedCertificate>
        rowKey="id"
        size="small"
        loading={isLoading}
        dataSource={data}
        pagination={false}
        scroll={{ x: true }}
        locale={{ emptyText: 'No certificates issued yet' }}
        columns={[
          { title: 'No.', dataIndex: 'serialNo' },
          { title: 'Certificate', dataIndex: 'title' },
          { title: 'Issued', dataIndex: 'issuedOn', render: formatDate },
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
      {issuing && (
        <IssueCertificateDialog studentId={studentId} studentName={studentName} status={status} onClose={() => setIssuing(false)} />
      )}
    </Flex>
  )
}
