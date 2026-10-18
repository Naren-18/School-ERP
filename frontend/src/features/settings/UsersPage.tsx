import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Checkbox, Flex, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import type { Role } from '../../api/auth'
import { errorMessage } from '../../api/client'
import { usersApi, type User, type UserCreateInput } from '../../api/users'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/PageHeader'
import { useFormErrors } from '../../components/useFormErrors'
import { formatDateTime } from '../../utils/format'
import { keys } from './queries'

const ROLES: { value: Role; label: string; help: string }[] = [
  { value: 'PRINCIPAL', label: 'Principal', help: 'Everything, including settings and users' },
  { value: 'ADMIN', label: 'Admin', help: 'Everything except settings and users' },
  { value: 'ACCOUNTANT', label: 'Accountant', help: 'Fees and receipts; view students' },
  { value: 'TEACHER', label: 'Teacher', help: 'Attendance and marks for assigned sections' },
]
const roleLabel = (r: Role) => ROLES.find((x) => x.value === r)?.label ?? r

const PASSWORD_RULES = [
  { required: true, message: 'Password is required' },
  { min: 8, message: 'At least 8 characters' },
  { max: 100 },
]

type Dialog = { kind: 'create' } | { kind: 'edit'; user: User } | { kind: 'password'; user: User } | null

export function UsersPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const showErrors = useFormErrors()
  const { me } = useAuth()
  const [form] = Form.useForm()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [q, setQ] = useState('')
  const [role, setRole] = useState<Role>()
  const [active, setActive] = useState<boolean | undefined>(true)
  const [page, setPage] = useState(1)

  const params = { q: q || undefined, role, active, page: page - 1, size: 20 }
  const { data, isFetching } = useQuery({
    queryKey: [...keys.users, params],
    queryFn: () => usersApi.search(params),
    placeholderData: keepPreviousData,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: keys.users })
    queryClient.invalidateQueries({ queryKey: ['staff'] })
  }

  const save = useMutation({
    mutationFn: async ({ confirm: _confirm, ...values }: UserCreateInput & { confirm?: string }) => {
      if (dialog?.kind === 'create') return usersApi.create(values)
      if (dialog?.kind === 'edit') return usersApi.update(dialog.user.id, values)
      if (dialog?.kind === 'password') return usersApi.resetPassword(dialog.user.id, values.password)
    },
    onSuccess: () => {
      invalidate()
      message.success(dialog?.kind === 'password' ? 'Password reset' : 'User saved')
      setDialog(null)
    },
    onError: (e) => showErrors(form, e),
  })
  const toggleActive = useMutation({
    mutationFn: (u: User) => usersApi.setActive(u.id, !u.active),
    onSuccess: (u) => {
      invalidate()
      message.success(u.active ? `${u.fullName} can sign in again` : `${u.fullName} can no longer sign in`)
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const open = (d: NonNullable<Dialog>) => {
    setDialog(d)
    form.resetFields()
    if (d.kind === 'edit') form.setFieldsValue(d.user)
    if (d.kind === 'create') form.setFieldsValue({ roles: ['TEACHER'] })
  }

  const title = { create: 'Add staff member', edit: 'Edit staff member', password: 'Reset password' }

  return (
    <>
      <PageHeader
        title="Users"
        subtitle="Staff logins for your school. Usernames are unique within the school."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open({ kind: 'create' })}>
            Add staff
          </Button>
        }
      />
      <Card>
        <Flex gap={8} wrap style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="Search name, username or phone"
            style={{ maxWidth: 300 }}
            onSearch={(v) => {
              setQ(v.trim())
              setPage(1)
            }}
          />
          <Select
            allowClear
            placeholder="All roles"
            style={{ minWidth: 140 }}
            value={role}
            onChange={(v) => {
              setRole(v)
              setPage(1)
            }}
            options={ROLES}
          />
          <Select
            style={{ minWidth: 130 }}
            value={active === undefined ? 'all' : String(active)}
            onChange={(v) => {
              setActive(v === 'all' ? undefined : v === 'true')
              setPage(1)
            }}
            options={[
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' },
              { value: 'all', label: 'All' },
            ]}
          />
        </Flex>
        <Table<User>
          rowKey="id"
          loading={isFetching}
          dataSource={data?.content ?? []}
          scroll={{ x: true }}
          pagination={{
            current: page,
            pageSize: 20,
            total: data?.totalElements ?? 0,
            onChange: setPage,
            showSizeChanger: false,
          }}
          columns={[
            {
              title: 'Name',
              dataIndex: 'fullName',
              render: (name: string, u) => (
                <Flex vertical>
                  <span>
                    {name} {!u.active && <Tag>Inactive</Tag>}
                  </span>
                  <span style={{ color: 'var(--ant-color-text-secondary)' }}>
                    {u.username}
                    {u.designation ? ` · ${u.designation}` : ''}
                  </span>
                </Flex>
              ),
            },
            {
              title: 'Roles',
              dataIndex: 'roles',
              render: (roles: Role[]) => roles.map((r) => <Tag key={r}>{roleLabel(r)}</Tag>),
            },
            { title: 'Phone', dataIndex: 'phone', render: (p: string | null) => p ?? '—' },
            { title: 'Last sign-in', dataIndex: 'lastLoginAt', render: formatDateTime },
            {
              title: '',
              key: 'actions',
              align: 'right',
              render: (_, u) => (
                <Space wrap>
                  <Button size="small" onClick={() => open({ kind: 'edit', user: u })}>
                    Edit
                  </Button>
                  <Button size="small" onClick={() => open({ kind: 'password', user: u })}>
                    Reset password
                  </Button>
                  {u.id !== me?.user.id && (
                    <Popconfirm
                      title={u.active ? `Deactivate ${u.fullName}?` : `Reactivate ${u.fullName}?`}
                      description={u.active ? 'They will be signed out and cannot sign in.' : undefined}
                      onConfirm={() => toggleActive.mutate(u)}
                    >
                      <Button size="small" danger={u.active}>
                        {u.active ? 'Deactivate' : 'Activate'}
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={dialog ? `${title[dialog.kind]}${dialog.kind !== 'create' ? `: ${dialog.user.fullName}` : ''}` : ''}
        open={dialog !== null}
        onCancel={() => setDialog(null)}
        onOk={() => form.submit()}
        okText="Save"
        confirmLoading={save.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          {dialog?.kind === 'create' && (
            <Form.Item
              name="username"
              label="Username"
              rules={[
                { required: true, message: 'Username is required' },
                { pattern: /^[a-zA-Z0-9._-]{3,60}$/, message: '3-60 letters, digits, dot, dash or underscore' },
              ]}
            >
              <Input autoCapitalize="none" autoComplete="off" />
            </Form.Item>
          )}
          {dialog?.kind !== 'password' && (
            <>
              <Form.Item name="fullName" label="Full name" rules={[{ required: true, whitespace: true }, { max: 120 }]}>
                <Input />
              </Form.Item>
              <Form.Item name="designation" label="Designation" rules={[{ max: 60 }]}>
                <Input placeholder="e.g. Maths Teacher" />
              </Form.Item>
              <Flex gap={12} wrap>
                <Form.Item
                  name="phone"
                  label="Phone"
                  style={{ flex: '1 1 180px' }}
                  rules={[{ pattern: /^[0-9+\- ]{6,15}$/, message: 'Enter a valid phone number' }]}
                >
                  <Input inputMode="tel" />
                </Form.Item>
                <Form.Item
                  name="email"
                  label="Email"
                  style={{ flex: '1 1 180px' }}
                  rules={[{ type: 'email', message: 'Enter a valid email' }]}
                >
                  <Input inputMode="email" />
                </Form.Item>
              </Flex>
              <Form.Item name="roles" label="Roles" rules={[{ required: true, message: 'Choose at least one role' }]}>
                <Checkbox.Group>
                  <Flex vertical gap={4}>
                    {ROLES.map((r) => (
                      <Checkbox key={r.value} value={r.value}>
                        <b>{r.label}</b> <span style={{ color: 'var(--ant-color-text-secondary)' }}>— {r.help}</span>
                      </Checkbox>
                    ))}
                  </Flex>
                </Checkbox.Group>
              </Form.Item>
            </>
          )}
          {dialog?.kind !== 'edit' && (
            <>
              <Form.Item name="password" label={dialog?.kind === 'password' ? 'New password' : 'Password'} rules={PASSWORD_RULES}>
                <Input.Password autoComplete="new-password" />
              </Form.Item>
              <Form.Item
                name="confirm"
                label="Confirm password"
                dependencies={['password']}
                rules={[
                  { required: true, message: 'Please confirm the password' },
                  ({ getFieldValue }) => ({
                    validator: (_, v) =>
                      !v || v === getFieldValue('password')
                        ? Promise.resolve()
                        : Promise.reject(new Error('Passwords do not match')),
                  }),
                ]}
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </>
          )}
          {/* Lets Enter submit; the visible Save button lives in the modal footer. */}
          <button type="submit" hidden />
        </Form>
      </Modal>
    </>
  )
}
