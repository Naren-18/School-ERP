import { useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, ColorPicker, Flex, Form, Input, Row, Select, Skeleton, Typography, Upload } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { schoolApi, type SchoolProfileInput } from '../../api/school'
import { PageHeader } from '../../components/PageHeader'
import { SchoolLogo } from '../../components/SchoolLogo'
import { useFormErrors } from '../../components/useFormErrors'
import { keys, useSchool } from './queries'

const BOARDS = [
  { value: 'STATE', label: 'State Board' },
  { value: 'CBSE', label: 'CBSE' },
  { value: 'ICSE', label: 'ICSE' },
  { value: 'OTHER', label: 'Other' },
]

const MEDIUMS = ['ENGLISH', 'TELUGU', 'HINDI', 'URDU'].map((m) => ({ value: m, label: m[0] + m.slice(1).toLowerCase() }))

export function SchoolProfilePage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<SchoolProfileInput>()
  const { data: school, isLoading } = useSchool()

  useEffect(() => {
    if (school) form.setFieldsValue(school)
  }, [school, form])

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: keys.school })
    queryClient.invalidateQueries({ queryKey: keys.me })
  }

  const save = useMutation({
    mutationFn: schoolApi.updateProfile,
    onSuccess: () => {
      refresh()
      message.success('School profile saved')
    },
    onError: (e) => showErrors(form, e),
  })

  const upload = useMutation({
    mutationFn: schoolApi.uploadLogo,
    onSuccess: () => {
      refresh()
      message.success('Logo updated')
    },
    onError: (e) => showErrors(null, e),
  })

  if (isLoading || !school) {
    return <Skeleton active />
  }

  return (
    <>
      <PageHeader title="School profile" subtitle={`School code: ${school.code}`} />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card title="Details">
            <Form
              form={form}
              layout="vertical"
              onFinish={(values) =>
                save.mutate({
                  ...values,
                  primaryColor:
                    typeof values.primaryColor === 'string'
                      ? values.primaryColor
                      : (values.primaryColor as { toHexString: () => string }).toHexString(),
                })
              }
            >
              <Form.Item name="name" label="School name" rules={[{ required: true, whitespace: true }, { max: 150 }]}>
                <Input />
              </Form.Item>
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item name="board" label="Board" rules={[{ required: true }]}>
                    <Select options={BOARDS} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="medium" label="Medium" rules={[{ required: true }]}>
                    <Select options={MEDIUMS} />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="address" label="Address" rules={[{ max: 500 }]}>
                <Input.TextArea rows={2} />
              </Form.Item>
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="phone"
                    label="Phone"
                    rules={[{ pattern: /^[0-9+\- ]{6,15}$/, message: 'Enter a valid phone number' }]}
                  >
                    <Input inputMode="tel" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
                    <Input inputMode="email" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="primaryColor" label="Theme colour" rules={[{ required: true }]}>
                <ColorPicker disabledAlpha showText />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={save.isPending}>
                Save
              </Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Logo">
            <Flex vertical align="center" gap={16}>
              <SchoolLogo name={school.name} logoUrl={school.logoUrl} color={school.primaryColor} size={96} />
              <Upload
                accept="image/png,image/jpeg,image/webp"
                showUploadList={false}
                beforeUpload={(file) => {
                  if (file.size > 1024 * 1024) {
                    message.error('Logo must be 1 MB or smaller')
                  } else {
                    upload.mutate(file)
                  }
                  return false
                }}
              >
                <Button icon={<UploadOutlined />} loading={upload.isPending}>
                  Upload new logo
                </Button>
              </Upload>
              <Typography.Text type="secondary" style={{ textAlign: 'center' }}>
                PNG, JPEG or WebP, up to 1 MB. A square image looks best.
              </Typography.Text>
            </Flex>
          </Card>
        </Col>
      </Row>
    </>
  )
}
