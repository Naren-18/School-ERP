import { useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Checkbox, Col, Form, Input, Row, Skeleton, Switch, Typography } from 'antd'
import { WhatsAppOutlined } from '@ant-design/icons'
import { schoolApi, type DayOfWeek, type SchoolSettings } from '../../api/school'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { keys, useSchool } from './queries'

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const MODULES: { key: keyof SchoolSettings['modules']; label: string; help: string }[] = [
  { key: 'attendance', label: 'Attendance', help: 'Daily attendance marking and reports' },
  { key: 'fees', label: 'Fees', help: 'Fee plans, collection and receipts' },
  { key: 'exams', label: 'Exams', help: 'Marks entry and report cards' },
  { key: 'certificates', label: 'Certificates', help: 'Bonafide and transfer certificates' },
]

const MESSAGES: { key: keyof SchoolSettings['messages']; label: string; help: string }[] = [
  { key: 'absence', label: 'Absence alerts', help: 'Sent from Mark attendance, for the students you tick' },
  { key: 'feeReminder', label: 'Fee reminders', help: 'Sent from Fee reports → Dues, for the students you tick' },
  { key: 'paymentReceived', label: 'Payment confirmations', help: 'Sent automatically for every receipt' },
  { key: 'resultsPublished', label: 'Results messages', help: 'Sent from Results once an exam is published' },
]

const PREFIX_RULES = [
  { required: true, message: 'Required' },
  { pattern: /^[A-Z0-9]{1,10}$/, message: 'Use 1-10 capital letters or digits' },
]

export function SchoolPreferencesPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<SchoolSettings>()
  const { data: school, isLoading } = useSchool()

  useEffect(() => {
    if (school) form.setFieldsValue(school.settings)
  }, [school, form])

  const save = useMutation({
    mutationFn: schoolApi.updateSettings,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.school })
      queryClient.invalidateQueries({ queryKey: keys.me })
      message.success('Settings saved')
    },
    onError: (e) => showErrors(form, e),
  })

  if (isLoading || !school) {
    return <Skeleton active />
  }

  return (
    <>
      <PageHeader title="Modules & preferences" />
      <Form form={form} layout="vertical" onFinish={(values) => save.mutate(values)}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="Modules" style={{ height: '100%' }}>
              <Typography.Paragraph type="secondary">
                Switched-off modules disappear from the menu for everyone in your school.
              </Typography.Paragraph>
              {MODULES.map((m) => (
                <Form.Item key={m.key} name={['modules', m.key]} valuePropName="checked" label={m.label} extra={m.help}>
                  <Switch />
                </Form.Item>
              ))}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="Numbering" style={{ marginBottom: 16 }}>
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item name="receiptPrefix" label="Receipt prefix" rules={PREFIX_RULES} normalize={(v: string) => v?.toUpperCase()}>
                    <Input maxLength={10} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="certificatePrefix"
                    label="Certificate prefix"
                    rules={PREFIX_RULES}
                    normalize={(v: string) => v?.toUpperCase()}
                  >
                    <Input maxLength={10} />
                  </Form.Item>
                </Col>
              </Row>
            </Card>
            <Card title="Weekly off days" style={{ marginBottom: 16 }}>
              <Form.Item
                name="weekOffDays"
                rules={[
                  {
                    validator: (_, v: DayOfWeek[] = []) =>
                      v.length < 7 ? Promise.resolve() : Promise.reject(new Error('At least one working day is required')),
                  },
                ]}
              >
                <Checkbox.Group
                  options={DAYS.map((d) => ({ value: d, label: d[0] + d.slice(1, 3).toLowerCase() }))}
                />
              </Form.Item>
            </Card>
            <Card title="Report card" style={{ marginBottom: 16 }}>
              <Form.Item name={['reportCard', 'showRank']} valuePropName="checked" label="Show rank in class">
                <Switch />
              </Form.Item>
              <Form.Item name={['reportCard', 'showAttendance']} valuePropName="checked" label="Show attendance">
                <Switch />
              </Form.Item>
            </Card>
            <Card
              title={
                <span>
                  <WhatsAppOutlined style={{ color: '#128c7e', marginRight: 8 }} />
                  WhatsApp messages to parents
                </span>
              }
            >
              <Typography.Paragraph type="secondary">
                Each message is charged by WhatsApp. Only parents who agreed on the admission form are messaged.
              </Typography.Paragraph>
              {MESSAGES.map((m) => (
                <Form.Item key={m.key} name={['messages', m.key]} valuePropName="checked" label={m.label} extra={m.help}>
                  <Switch />
                </Form.Item>
              ))}
            </Card>
          </Col>
        </Row>
        <Button type="primary" htmlType="submit" loading={save.isPending} style={{ marginTop: 16 }}>
          Save settings
        </Button>
      </Form>
    </>
  )
}
