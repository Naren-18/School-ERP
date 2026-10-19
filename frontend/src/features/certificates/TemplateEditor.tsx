import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Col, Collapse, Flex, Input, Popconfirm, Row, Skeleton, Space, Tag, Typography } from 'antd'
import { SaveOutlined, UndoOutlined } from '@ant-design/icons'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import { errorMessage } from '../../api/client'
import { templatesApi, type TemplateType } from '../../api/certificates'
import { PageHeader } from '../../components/PageHeader'
import { formatDateTime } from '../../utils/format'

/** Waits until the value has stopped changing for `ms`. */
function useDebounced<T>(value: T, ms: number) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return debounced
}

/**
 * Edit a school's template (certificate or report card): HTML on the left,
 * a live PDF preview with sample data on the right, placeholders to insert.
 */
export function TemplateEditor({ type, subtitle }: { type: TemplateType; subtitle: string }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const area = useRef<TextAreaRef>(null)
  const template = useQuery({ queryKey: ['template', type], queryFn: () => templatesApi.get(type) })
  /** Unsaved edits; undefined shows the saved template. */
  const [draft, setDraft] = useState<string>()
  const html = draft ?? template.data?.html ?? ''
  const previewHtml = useDebounced(html, 800)
  const preview = useQuery({
    queryKey: ['template-preview', type, previewHtml],
    queryFn: () => templatesApi.preview(type, previewHtml),
    enabled: previewHtml.trim().length > 0,
    retry: false,
    staleTime: Infinity,
  })
  const pdfUrl = useMemo(() => (preview.data ? URL.createObjectURL(preview.data) : undefined), [preview.data])
  useEffect(() => () => {
    if (pdfUrl) URL.revokeObjectURL(pdfUrl)
  }, [pdfUrl])

  const onSaved = (t: Awaited<ReturnType<typeof templatesApi.get>>) => {
    queryClient.setQueryData(['template', type], t)
    setDraft(undefined)
  }
  const save = useMutation({
    mutationFn: () => templatesApi.save(type, html),
    onSuccess: (t) => {
      onSaved(t)
      message.success('Template saved. New documents use it from now on.')
    },
    onError: (e) => message.error(errorMessage(e)),
  })
  const reset = useMutation({
    mutationFn: () => templatesApi.reset(type),
    onSuccess: (t) => {
      onSaved(t)
      message.success('Back to the built-in template')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  /** Inserts a placeholder at the cursor. */
  const insert = (token: string) => {
    const el = area.current?.resizableTextArea?.textArea
    const start = el?.selectionStart ?? html.length
    const end = el?.selectionEnd ?? html.length
    setDraft(html.slice(0, start) + token + html.slice(end))
    requestAnimationFrame(() => {
      el?.focus()
      el?.setSelectionRange(start + token.length, start + token.length)
    })
  }

  if (template.isLoading || !template.data) {
    return template.isError ? <Alert type="error" title={errorMessage(template.error)} /> : <Skeleton active />
  }
  const t = template.data
  const dirty = draft !== undefined && draft !== t.html

  return (
    <>
      <PageHeader
        title={`${t.title} template`}
        subtitle={subtitle}
        extra={
          <Space wrap>
            {t.custom ? (
              <Tag color="blue">Your version{t.updatedAt ? ` · ${formatDateTime(t.updatedAt)}` : ''}</Tag>
            ) : (
              <Tag>Built-in</Tag>
            )}
            {t.custom && (
              <Popconfirm title="Go back to the built-in template?" description="Your version is removed." onConfirm={() => reset.mutate()}>
                <Button icon={<UndoOutlined />} loading={reset.isPending}>
                  Reset to built-in
                </Button>
              </Popconfirm>
            )}
            <Button type="primary" icon={<SaveOutlined />} disabled={!dirty} loading={save.isPending} onClick={() => save.mutate()}>
              Save
            </Button>
          </Space>
        }
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card size="small" title="HTML" styles={{ body: { padding: 8 } }}>
            <Input.TextArea
              ref={area}
              value={html}
              onChange={(e) => setDraft(e.target.value)}
              spellCheck={false}
              autoSize={{ minRows: 18, maxRows: 32 }}
              style={{ fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12.5 }}
            />
            <Typography.Paragraph type="secondary" style={{ margin: '8px 4px 0', fontSize: 12 }}>
              Write XHTML: close every tag (<code>&lt;br/&gt;</code>), and use <code>&amp;amp;</code> for &amp;. Classes you can use:{' '}
              <code>header title border center right muted small big grid fields sign gap</code>. Images and links to other
              sites are not loaded.
            </Typography.Paragraph>
            <Collapse
              size="small"
              style={{ marginTop: 8 }}
              items={[
                {
                  key: 'p',
                  label: 'Placeholders (click to insert)',
                  children: (
                    <Flex vertical gap={4}>
                      {Object.entries(t.blocks).map(([k, d]) => (
                        <Placeholder key={k} token={`{{{${k}}}}`} description={d} onInsert={insert} />
                      ))}
                      {Object.entries(t.values).map(([k, d]) => (
                        <Placeholder key={k} token={`{{${k}}}`} description={d} onInsert={insert} />
                      ))}
                    </Flex>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title="Preview with sample data" styles={{ body: { padding: 0 } }}>
            {preview.isError ? (
              <Alert type="warning" showIcon style={{ margin: 12 }} title={errorMessage(preview.error)} />
            ) : pdfUrl ? (
              <iframe title="Preview" src={pdfUrl} style={{ width: '100%', height: 720, border: 0 }} />
            ) : (
              <Skeleton active style={{ padding: 16 }} />
            )}
          </Card>
        </Col>
      </Row>
    </>
  )
}

function Placeholder({ token, description, onInsert }: { token: string; description: string; onInsert: (t: string) => void }) {
  return (
    <Flex gap={8} align="baseline" wrap>
      <Button size="small" type="link" style={{ padding: 0, fontFamily: 'ui-monospace, Consolas, monospace' }} onClick={() => onInsert(token)}>
        {token}
      </Button>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {description}
      </Typography.Text>
    </Flex>
  )
}
