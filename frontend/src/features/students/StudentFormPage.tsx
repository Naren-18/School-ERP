import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, App, Button, Card, Checkbox, Col, DatePicker, Flex, Form, Input, InputNumber, Row, Select, Skeleton, Space,
  Typography, Upload,
} from 'antd'
import { CameraOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import {
  studentsApi, type GuardianInput, type StudentDetail, type StudentDetailsInput,
} from '../../api/students'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { API_DATE_FORMAT, DATE_FORMAT } from '../../utils/format'
import { useYears } from '../settings/queries'
import { CATEGORIES, GENDERS, RELATIONS } from './labels'
import { SectionSelect } from './SectionSelect'

type FormValues = Omit<StudentDetailsInput, 'admissionDate' | 'dateOfBirth'> & {
  admissionDate: Dayjs
  dateOfBirth: Dayjs
  sectionId?: number
  rollNo?: number | null
}

const MOBILE = /^[6-9]\d{9}$/

const toForm = (s: StudentDetail): FormValues => ({
  ...s,
  admissionDate: dayjs(s.admissionDate),
  dateOfBirth: dayjs(s.dateOfBirth),
  aadhaar: undefined,
  guardians: s.guardians.map((g) => ({ ...g })),
})

const blank = (v?: string | null) => (v && v.trim() ? v.trim() : null)

/** An untouched contact row (e.g. the default "Mother" left empty) is ignored on save. */
const isBlankContact = (g: GuardianInput) =>
  !blank(g.fullName) && !blank(g.phone) && !blank(g.occupation) && !blank(g.email)

/** Admission (new) and edit form. Backend field errors arrive as "student.firstName" etc. */
export function StudentFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const showErrors = useFormErrors()
  const [form] = Form.useForm<FormValues>()
  const [photo, setPhoto] = useState<File | null>(null)
  const photoPreview = useMemo(() => (photo ? URL.createObjectURL(photo) : undefined), [photo])
  useEffect(() => () => (photoPreview ? URL.revokeObjectURL(photoPreview) : undefined), [photoPreview])
  const currentYear = useYears().data?.find((y) => y.current)

  const existing = useQuery({
    queryKey: ['student', Number(id)],
    queryFn: () => studentsApi.get(Number(id)),
    enabled: editing,
  })
  const suggested = useQuery({
    queryKey: ['next-admission-no'],
    queryFn: studentsApi.nextAdmissionNo,
    enabled: !editing,
  })

  useEffect(() => {
    if (existing.data) form.setFieldsValue(toForm(existing.data))
  }, [existing.data, form])
  useEffect(() => {
    if (suggested.data && !form.getFieldValue('admissionNo')) {
      form.setFieldsValue({ admissionNo: suggested.data.admissionNo })
    }
  }, [suggested.data, form])

  const save = useMutation({
    mutationFn: async (v: FormValues) => {
      const student: StudentDetailsInput = {
        admissionNo: v.admissionNo.trim(),
        admissionDate: v.admissionDate.format(API_DATE_FORMAT),
        firstName: v.firstName.trim(),
        lastName: blank(v.lastName),
        dateOfBirth: v.dateOfBirth.format(API_DATE_FORMAT),
        gender: v.gender,
        motherTongue: blank(v.motherTongue),
        socialCategory: v.socialCategory ?? null,
        religion: blank(v.religion),
        caste: blank(v.caste),
        // Empty keeps the stored number (the API never sends it back, so there is nothing to show).
        aadhaar: v.aadhaar?.trim() ? v.aadhaar.trim() : undefined,
        identificationMarks: blank(v.identificationMarks),
        address: blank(v.address),
        previousSchool: blank(v.previousSchool),
        previousTcNo: blank(v.previousTcNo),
        whatsappConsent: !!v.whatsappConsent,
        guardians: v.guardians.filter((g) => !isBlankContact(g)).map((g: GuardianInput) => ({
          ...g,
          fullName: g.fullName.trim(),
          occupation: blank(g.occupation),
          phone: blank(g.phone),
          email: blank(g.email),
        })),
      }
      const saved = editing
        ? await studentsApi.update(Number(id), student)
        : await studentsApi.create(student, { sectionId: v.sectionId!, rollNo: v.rollNo ?? null })
      return photo ? studentsApi.uploadPhoto(saved.id, photo) : saved
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ['students'] })
      queryClient.invalidateQueries({ queryKey: ['next-admission-no'] })
      queryClient.setQueryData(['student', s.id], s)
      message.success(editing ? 'Student updated' : `${s.fullName} admitted`)
      navigate(`/students/${s.id}`, { replace: true })
    },
    onError: (e) => showErrors(form, stripPrefix(e)),
  })

  if (editing && existing.isLoading) {
    return <Skeleton active />
  }
  const sensitiveVisible = !editing || existing.data?.sensitiveVisible

  return (
    <>
      <PageHeader
        title={editing ? `Edit ${existing.data?.fullName ?? 'student'}` : 'Admit student'}
        subtitle={!editing && currentYear ? `Admission into ${currentYear.name}` : undefined}
      />
      {!editing && !currentYear && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} title="Create the current academic year and its sections first." />
      )}
      <Form<FormValues>
        form={form}
        layout="vertical"
        scrollToFirstError
        initialValues={{
          admissionDate: dayjs(),
          gender: undefined,
          guardians: [
            { relation: 'FATHER', fullName: '', primary: true },
            { relation: 'MOTHER', fullName: '' },
          ],
        }}
        onFinish={(v) => save.mutate(v)}
        onFinishFailed={({ errorFields }) =>
          message.error(
            errorFields.length === 1
              ? `Please fix: ${errorFields[0].errors[0]}`
              : `Please fix the ${errorFields.length} highlighted fields`,
          )
        }
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={16}>
            <Card title="Admission" style={{ marginBottom: 16 }}>
              <Row gutter={12}>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item name="admissionNo" label="Admission no." rules={[{ required: true, whitespace: true }, { max: 30 }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={8}>
                  <Form.Item name="admissionDate" label="Admission date" rules={[{ required: true }]}>
                    <DatePicker format={DATE_FORMAT} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
                  </Form.Item>
                </Col>
                {!editing && (
                  <>
                    <Col xs={24} sm={12} md={8}>
                      <Form.Item name="sectionId" label="Class – section" rules={[{ required: true, message: 'Choose a section' }]}>
                        <SectionSelect yearId={currentYear?.id} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} sm={12} md={8}>
                      <Form.Item name="rollNo" label="Roll no." extra="Leave empty for the next number.">
                        <InputNumber min={1} max={999} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </>
                )}
              </Row>
            </Card>

            <Card title="Student" style={{ marginBottom: 16 }}>
              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item name="firstName" label="First name" rules={[{ required: true, whitespace: true }, { max: 60 }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="lastName" label="Surname" rules={[{ max: 60 }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item name="dateOfBirth" label="Date of birth" rules={[{ required: true }]}>
                    <DatePicker format={DATE_FORMAT} style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item name="gender" label="Gender" rules={[{ required: true }]}>
                    <Select options={GENDERS} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item name="motherTongue" label="Mother tongue" rules={[{ max: 30 }]}>
                    <Input placeholder="e.g. Telugu" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="address" label="Address" rules={[{ max: 500 }]}>
                    <Input.TextArea rows={2} />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="identificationMarks" label="Identification marks" rules={[{ max: 500 }]}>
                    <Input placeholder="e.g. Mole on left cheek" />
                  </Form.Item>
                </Col>
              </Row>
            </Card>

            <Card title="Parents / guardians" style={{ marginBottom: 16 }}>
              <Form.List
                name="guardians"
                rules={[
                  {
                    validator: async (_, list: GuardianInput[] = []) => {
                      if (!list.some((g) => g?.phone && MOBILE.test(g.phone))) {
                        throw new Error('At least one contact needs a mobile number')
                      }
                    },
                  },
                ]}
              >
                {(fields, { add, remove }, { errors }) => (
                  <>
                    {fields.map((field, index) => (
                      <Card
                        key={field.key}
                        size="small"
                        type="inner"
                        style={{ marginBottom: 12 }}
                        title={`Contact ${index + 1}`}
                        extra={
                          fields.length > 1 && (
                            <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} aria-label="Remove" />
                          )
                        }
                      >
                        <Row gutter={12}>
                          <Col xs={24} sm={8}>
                            <Form.Item name={[field.name, 'relation']} label="Relation" rules={[{ required: true }]}>
                              <Select options={RELATIONS} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={16}>
                            <Form.Item
                              name={[field.name, 'fullName']}
                              label="Name"
                              dependencies={[['guardians', field.name, 'phone'], ['guardians', field.name, 'occupation'], ['guardians', field.name, 'email']]}
                              rules={[
                                { max: 120 },
                                // A completely empty contact is simply skipped; once anything is
                                // filled in, the name is required.
                                ({ getFieldValue }) => ({
                                  validator: (_, name?: string) => {
                                    const g = getFieldValue(['guardians', field.name]) as GuardianInput | undefined
                                    return name?.trim() || !g || isBlankContact(g)
                                      ? Promise.resolve()
                                      : Promise.reject(new Error('Enter the name, or clear this contact'))
                                  },
                                }),
                              ]}
                            >
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={8}>
                            <Form.Item
                              name={[field.name, 'phone']}
                              label="Mobile"
                              rules={[{ pattern: MOBILE, message: 'Enter a 10-digit mobile number' }]}
                            >
                              <Input inputMode="numeric" maxLength={10} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={8}>
                            <Form.Item name={[field.name, 'occupation']} label="Occupation" rules={[{ max: 80 }]}>
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={8}>
                            <Form.Item name={[field.name, 'email']} label="Email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
                              <Input inputMode="email" />
                            </Form.Item>
                          </Col>
                          <Col xs={24}>
                            <Form.Item name={[field.name, 'primary']} valuePropName="checked" style={{ marginBottom: 0 }}>
                              <Checkbox
                                onChange={(e) => {
                                  // Only one primary contact.
                                  if (e.target.checked) {
                                    const all = form.getFieldValue('guardians') as GuardianInput[]
                                    form.setFieldValue('guardians', all.map((g, i) => ({ ...g, primary: i === index })))
                                  }
                                }}
                              >
                                Primary contact (SMS and calls go here first)
                              </Checkbox>
                            </Form.Item>
                          </Col>
                        </Row>
                      </Card>
                    ))}
                    {fields.length < 4 && (
                      <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ relation: 'GUARDIAN', fullName: '' })}>
                        Add another contact
                      </Button>
                    )}
                    <Form.ErrorList errors={errors} />
                  </>
                )}
              </Form.List>
              <Form.Item
                name="whatsappConsent"
                valuePropName="checked"
                style={{ marginTop: 16, marginBottom: 0 }}
                extra="Absence alerts, fee reminders, payment confirmations and results go to the primary contact's mobile number."
              >
                <Checkbox>Parent agrees to receive WhatsApp messages from the school</Checkbox>
              </Form.Item>
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <Card title="Photo" style={{ marginBottom: 16 }}>
              <Flex vertical align="center" gap={12}>
                {photoPreview ? (
                  <img src={photoPreview} alt="" style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 8 }} />
                ) : (
                  <Typography.Text type="secondary">
                    {existing.data?.hasPhoto ? 'A photo is on file. Choose a new one to replace it.' : 'No photo yet'}
                  </Typography.Text>
                )}
                <Upload
                  accept="image/png,image/jpeg,image/webp"
                  showUploadList={false}
                  beforeUpload={(file) => {
                    if (file.size > 2 * 1024 * 1024) message.error('Photo must be 2 MB or smaller')
                    else setPhoto(file)
                    return false
                  }}
                >
                  <Button icon={<CameraOutlined />}>{photo ? 'Change photo' : 'Choose photo'}</Button>
                </Upload>
              </Flex>
            </Card>

            {sensitiveVisible && (
              <Card title="Identity & category" style={{ marginBottom: 16 }} extra={<Typography.Text type="secondary">Principal/admin only</Typography.Text>}>
                <Form.Item
                  name="aadhaar"
                  label="Aadhaar number"
                  extra={
                    existing.data?.aadhaarMasked
                      ? `On file: ${existing.data.aadhaarMasked}. Leave empty to keep it.`
                      : 'Stored encrypted; only the last 4 digits are ever shown.'
                  }
                  rules={[{ pattern: /^[2-9]\d{3}\s?\d{4}\s?\d{4}$/, message: 'Enter 12 digits' }]}
                >
                  <Input inputMode="numeric" maxLength={14} autoComplete="off" />
                </Form.Item>
                <Form.Item name="socialCategory" label="Social category">
                  <Select allowClear options={CATEGORIES} />
                </Form.Item>
                <Form.Item name="religion" label="Religion" rules={[{ max: 30 }]}>
                  <Input />
                </Form.Item>
                <Form.Item name="caste" label="Caste" rules={[{ max: 50 }]}>
                  <Input />
                </Form.Item>
              </Card>
            )}

            <Card title="Previous school">
              <Form.Item name="previousSchool" label="School name" rules={[{ max: 150 }]}>
                <Input />
              </Form.Item>
              <Form.Item name="previousTcNo" label="TC number" rules={[{ max: 50 }]}>
                <Input />
              </Form.Item>
            </Card>
          </Col>
        </Row>

        <Space style={{ marginTop: 16 }}>
          <Button type="primary" htmlType="submit" size="large" loading={save.isPending}>
            {editing ? 'Save changes' : 'Admit student'}
          </Button>
          <Button size="large" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </Space>
      </Form>
    </>
  )
}

/**
 * The API nests fields ("student.firstName", "student.guardians[0].phone");
 * the form is flat, so map those names onto it.
 */
function stripPrefix(error: unknown): unknown {
  const data = (error as { response?: { data?: { errors?: Record<string, string> } } })?.response?.data
  if (data?.errors) {
    data.errors = Object.fromEntries(
      Object.entries(data.errors).map(([k, v]) => [k.replace(/^student\./, '').replace(/^enrollment\./, ''), v]),
    )
  }
  return error
}
