import { useSearchParams } from 'react-router-dom'
import { Segmented } from 'antd'
import type { CertificateType } from '../../api/certificates'
import { TemplateEditor } from './TemplateEditor'

/** Principal: edit the bonafide and TC templates. */
export function CertificateTemplatesPage() {
  const [params, setParams] = useSearchParams()
  const type: CertificateType = params.get('type') === 'TC' ? 'TC' : 'BONAFIDE'
  return (
    <>
      <Segmented
        style={{ marginBottom: 12 }}
        value={type}
        onChange={(v) => setParams({ type: String(v) }, { replace: true })}
        options={[
          { value: 'BONAFIDE', label: 'Bonafide' },
          { value: 'TC', label: 'Transfer certificate' },
        ]}
      />
      <TemplateEditor
        key={type}
        type={type}
        subtitle="Certificates already issued keep the version they were printed with."
      />
    </>
  )
}
