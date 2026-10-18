/**
 * Opens a PDF fetched with the auth header in a new tab (where it can be
 * printed). The tab is opened before the fetch so pop-up blockers allow it.
 */
export async function openPdf(fetchPdf: () => Promise<Blob>) {
  const tab = window.open('', '_blank')
  try {
    const url = URL.createObjectURL(await fetchPdf())
    if (tab) tab.location.href = url
    else window.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    tab?.close()
    throw e
  }
}
