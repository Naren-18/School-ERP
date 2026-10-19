import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, DatePicker, Form, Input, Modal, Radio } from 'antd'
import { EyeOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { certificatesApi, type CertificateType, type IssueRequest } from '../../api/certificates'
import { errorMessage } from '../../api/client'
import { API_DATE_FORMAT, DATE_FORMAT } from '../../utils/format'
import { openPdf } from '../../utils/openPdf'

interface Values {
  type: CertificateType
  purpose?: string
  leavingDate?: Dayjs
  reason?: string
  conduct?: string
  promotion?: string
  remarks?: string
}

interface Props {
  studentId: number
  studentName: string
  /** Student status: a bonafide needs ACTIVE; a TC is refused once TC_ISSUED. */
  status: string
  onClose: () => void
}

/** Issue a bonafide or TC: preview the PDF first, then issue (numbered and kept in the register). */
export function IssueCertificateDialog({ studentId, studentName, status, onClose }: Props) {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<Values>()
  const [previewing, setPreviewing] = useState(false)
  const type = Form.useWatch('type', form)
  const active = status === 'ACTIVE'

  const request = (v: Values): IssueRequest => ({
    type: v.type,
    purpose: v.purpose ?? null,
    tc:
      v.type === 'TC'
        ? {
            leavingDate: v.leavingDate?.format(API_DATE_FORMAT) ?? null,
            reason: v.reason ?? null,
            conduct: v.conduct ?? null,
            promotion: v.promotion ?? null,
            remarks: v.remarks ?? null,
          }
        : null,
  })

  const issue = useMutation({
    mutationFn: (v: Values) => certificatesApi.issue(studentId, request(v)),
    onSuccess: async (c) => {
      queryClient.invalidateQueries({ queryKey: ['certificates'] })
      queryClient.invalidateQueries({ queryKey: ['student', studentId] })
      queryClient.invalidateQueries({ queryKey: ['students'] })
      message.success(`${c.title} ${c.serialNo} issued`)
      onClose()
      await openPdf(() => certificatesApi.pdf(c.id)).catch((e) => message.error(errorMessage(e)))
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const preview = async () => {
    const v = await form.validateFields()
    setPreviewing(true)
    try {
      await openPdf(() => certificatesApi.preview(studentId, request(v)))
    } catch (e) {
      message.error(errorMessage(e))
    } finally {
      setPreviewing(false)
    }
  }

  const submit = (v: Values) => {
    if (v.type === 'TC' && active) {
      modal.confirm({
        title: `Issue a TC to ${studentName}?`,
        content: 'The student is marked as left on the date of leaving, and a TC can only be issued once.',
        okText: 'Issue TC',
        onOk: () => issue.mutateAsync(v),
      })
    } else {
      issue.mutate(v)
    }
  }

  return (
    <Modal
      open
      title={`Certificate for ${studentName}`}
      onCancel={onClose}
      destroyOnHidden
      footer={[
        <Button key="preview" icon={<EyeOutlined />} loading={previewing} onClick={preview}>
          Preview
        </Button>,
        <Button key="issue" type="primary" loading={issue.isPending} onClick={() => form.submit()}>
          Issue
        </Button>,
      ]}
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={submit}
        initialValues={{ type: active ? 'BONAFIDE' : 'TC', leavingDate: active ? dayjs() : undefined, conduct: 'Good' }}
      >
        <Form.Item name="type" label="Certificate">
          <Radio.Group
            optionType="button"
            options={[
              { value: 'BONAFIDE', label: 'Bonafide', disabled: !active },
              { value: 'TC', label: 'Transfer certificate', disabled: status === 'TC_ISSUED' },
            ]}
          />
        </Form.Item>
        {!active && type === 'TC' && (
          <Alert type="info" showIcon style={{ marginBottom: 12 }} title="The student has already left; the TC uses their leaving date." />
        )}
        <Form.Item name="purpose" label="Purpose" rules={[{ max: 150 }]} extra={type === 'BONAFIDE' ? 'Printed as "issued for the purpose of …"' : undefined}>
          <Input placeholder={type === 'BONAFIDE' ? 'e.g. applying for a scholarship' : 'e.g. admission to another school'} />
        </Form.Item>
        {type === 'TC' && (
          <>
            {active && (
              <Form.Item name="leavingDate" label="Date of leaving" rules={[{ required: true, message: 'Choose the date' }]}>
                <DatePicker format={DATE_FORMAT} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            )}
            <Form.Item
              name="reason"
              label="Reason for leaving"
              rules={[{ required: active, whitespace: true, message: 'Reason is required' }, { max: 300 }]}
            >
              <Input placeholder="e.g. Parent's transfer" />
            </Form.Item>
            <Form.Item name="conduct" label="Conduct" rules={[{ max: 60 }]}>
              <Input />
            </Form.Item>
            <Form.Item name="promotion" label="Qualified for promotion?" rules={[{ max: 100 }]}>
              <Input placeholder="e.g. Yes, to Class 6" />
            </Form.Item>
            <Form.Item name="remarks" label="Remarks" rules={[{ max: 300 }]}>
              <Input.TextArea autoSize={{ minRows: 1, maxRows: 4 }} />
            </Form.Item>
          </>
        )}
        <button type="submit" hidden />
      </Form>
    </Modal>
  )
}
