import type { HookAPI } from 'antd/es/modal/useModal'
import type { SendReport } from '../api/notifications'
import { SendReportSummary } from './SendReportSummary'

/** Opens the summary of a send in a dialog: how many were sent, and why the others were not. */
export function showSendReport(modal: HookAPI, report: SendReport) {
  const parents = `${report.sent} parent${report.sent === 1 ? '' : 's'}`
  const title =
    report.sent > 0 && report.failed === 0
      ? report.connected
        ? `WhatsApp sent to ${parents}`
        : `Message for ${parents} written to the log (WhatsApp not connected)`
      : report.sent === 0 && report.failed === 0
        ? 'No messages sent'
        : 'Some messages were not sent'
  const show = report.failed > 0 || report.sent === 0 ? modal.warning : modal.success
  show({ title, content: <SendReportSummary report={report} />, okText: 'Done' })
}
