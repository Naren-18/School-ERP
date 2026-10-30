import { Alert, Flex, List, Tag, Typography } from 'antd'
import type { SendReport } from '../api/notifications'

/** Shown when the server has no WhatsApp keys yet: nothing reaches phones. */
export function NotConnectedNote() {
  return (
    <Alert
      type="info"
      showIcon
      title="WhatsApp is not connected yet"
      description="Messages are written to the server log only. Add the WhatsApp keys on the server to send them to phones."
    />
  )
}

/** Sent / failed / skipped counts, and why each student was not messaged. */
export function SendReportSummary({ report }: { report: SendReport }) {
  const problems = report.outcomes.filter((o) => o.result !== 'SENT')
  return (
    <Flex vertical gap={12}>
      <Flex gap={8} wrap>
        <Tag color="green">{report.sent} sent</Tag>
        {report.failed > 0 && <Tag color="red">{report.failed} failed</Tag>}
        {report.skipped > 0 && <Tag>{report.skipped} skipped</Tag>}
      </Flex>
      {!report.connected && <NotConnectedNote />}
      {problems.length > 0 && (
        <List
          size="small"
          bordered
          style={{ maxHeight: 260, overflowY: 'auto' }}
          dataSource={problems}
          renderItem={(o) => (
            <List.Item>
              <Flex vertical style={{ minWidth: 0 }}>
                <Typography.Text strong>{o.studentName ?? `Student ${o.studentId}`}</Typography.Text>
                <Typography.Text type={o.result === 'FAILED' ? 'danger' : 'secondary'}>
                  {o.result === 'FAILED' ? 'Failed: ' : 'Skipped: '}
                  {o.detail}
                </Typography.Text>
              </Flex>
            </List.Item>
          )}
        />
      )}
    </Flex>
  )
}

