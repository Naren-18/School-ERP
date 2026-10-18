import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Flex, Result, Table, Typography, Upload } from 'antd'
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import { errorMessage } from '../../api/client'
import { studentsApi, type ImportReport } from '../../api/students'
import { PageHeader } from '../../components/PageHeader'
import { saveBlob } from '../../utils/download'

/** Check the file first (nothing saved), then import if every row is valid. */
export function StudentImportPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)

  const template = useMutation({
    mutationFn: studentsApi.importTemplate,
    onSuccess: (blob) => saveBlob(blob, 'students-template.csv'),
    onError: (e) => message.error(errorMessage(e)),
  })
  const run = useMutation({
    mutationFn: ({ f, dryRun }: { f: File; dryRun: boolean }) => studentsApi.importCsv(f, dryRun),
    onSuccess: (r) => {
      setReport(r)
      if (!r.dryRun && r.created > 0) queryClient.invalidateQueries({ queryKey: ['students'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  if (report && !report.dryRun && report.created > 0) {
    return (
      <Result
        status="success"
        title={`${report.created} students imported`}
        extra={[
          <Button key="list" type="primary" onClick={() => navigate('/students')}>
            View students
          </Button>,
          <Button key="again" onClick={() => { setFile(null); setReport(null) }}>
            Import another file
          </Button>,
        ]}
      />
    )
  }

  return (
    <>
      <PageHeader
        title="Import students"
        subtitle="Admit many students at once into the current academic year."
        extra={
          <Button icon={<DownloadOutlined />} loading={template.isPending} onClick={() => template.mutate()}>
            Download template
          </Button>
        }
      />
      <Card style={{ marginBottom: 16 }}>
        <Typography.Paragraph>
          Fill the template in Excel or Google Sheets and save it as <b>CSV</b>. Dates are <b>DD-MM-YYYY</b>; class and
          section must match names under Setup (e.g. "Class 5" and "A"). Each student needs at least one parent mobile
          number. Nothing is saved until every row is valid.
        </Typography.Paragraph>
        <Upload.Dragger
          accept=".csv,text/csv"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(f) => {
            setFile(f)
            setReport(null)
            run.mutate({ f, dryRun: true })
            return false
          }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p>{file ? file.name : 'Click or drop a CSV file here'}</p>
        </Upload.Dragger>
      </Card>

      {report && (
        <Card title="Check result">
          {report.errors.length === 0 ? (
            <Flex vertical gap={12} align="flex-start">
              <Alert type="success" showIcon title={`All ${report.totalRows} rows are valid.`} />
              <Button type="primary" loading={run.isPending} onClick={() => file && run.mutate({ f: file, dryRun: false })}>
                Import {report.totalRows} students
              </Button>
            </Flex>
          ) : (
            <>
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 12 }}
                title={`${report.totalRows - report.validRows} of ${report.totalRows} rows need fixing. Correct the file and upload it again.`}
              />
              <Table
                rowKey={(e) => `${e.row}-${e.field}-${e.message}`}
                size="small"
                scroll={{ x: true }}
                dataSource={report.errors}
                pagination={{ pageSize: 50, hideOnSinglePage: true }}
                columns={[
                  { title: 'Row', dataIndex: 'row', width: 70 },
                  { title: 'Column', dataIndex: 'field', width: 160 },
                  { title: 'Problem', dataIndex: 'message' },
                ]}
              />
            </>
          )}
        </Card>
      )}
    </>
  )
}
