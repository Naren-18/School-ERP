import { feesApi } from '../../api/fees'

/**
 * Opens a receipt PDF in a new tab (where it can be printed). The tab is
 * opened before the fetch so pop-up blockers allow it.
 */
export async function openReceiptPdf(receiptId: number) {
  const tab = window.open('', '_blank')
  try {
    const blob = await feesApi.receiptPdf(receiptId)
    const url = URL.createObjectURL(blob)
    if (tab) tab.location.href = url
    else window.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    tab?.close()
    throw e
  }
}
