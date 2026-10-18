import { saveBlob } from './download'

/** Builds a CSV (Excel-friendly: UTF-8 BOM, CRLF) from rows and downloads it. */
export function downloadCsv(fileName: string, header: string[], rows: (string | number | null | undefined)[][]) {
  const cell = (v: string | number | null | undefined) => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const text = [header, ...rows].map((r) => r.map(cell).join(',')).join('\r\n')
  saveBlob(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }), fileName)
}
