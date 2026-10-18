import { feesApi } from '../../api/fees'
import { openPdf } from '../../utils/openPdf'

/** Opens a receipt PDF in a new tab for printing. */
export const openReceiptPdf = (receiptId: number) => openPdf(() => feesApi.receiptPdf(receiptId))
