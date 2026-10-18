import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, ConfigProvider, Flex, Form, Input, Typography } from 'antd'
import { BankOutlined, LockOutlined, UserOutlined } from '@ant-design/icons'
import { authApi, type LoginRequest } from '../../api/auth'
import { errorMessage, isNotFound } from '../../api/client'
import { useAuth } from '../../auth/useAuth'
import { SchoolLogo } from '../../components/SchoolLogo'
import { DEFAULT_PRIMARY } from '../../layout/BrandTheme'

const LAST_SCHOOL_KEY = 'erp.lastSchoolCode'

function readLastSchool(): string {
  try {
    return localStorage.getItem(LAST_SCHOOL_KEY) ?? ''
  } catch {
    return ''
  }
}

function useDebounced(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form] = Form.useForm<LoginRequest>()
  const initialCode = readLastSchool()
  const schoolCode = (Form.useWatch('schoolCode', form) ?? initialCode).trim()
  const debouncedCode = useDebounced(schoolCode, 400)

  const branding = useQuery({
    queryKey: ['branding', debouncedCode.toLowerCase()],
    queryFn: () => authApi.branding(debouncedCode),
    enabled: debouncedCode.length >= 2,
    retry: false,
    staleTime: 5 * 60_000,
  })

  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data, request) => {
      try {
        localStorage.setItem(LAST_SCHOOL_KEY, request.schoolCode.trim())
      } catch {
        // ignore
      }
      signIn(data.accessToken)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    },
  })

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const school = schoolCode.length >= 2 ? branding.data : undefined
  const color = school?.primaryColor || DEFAULT_PRIMARY

  return (
    <ConfigProvider theme={{ token: { colorPrimary: color } }}>
      <Flex
        align="center"
        justify="center"
        style={{ minHeight: '100vh', padding: 16, background: `linear-gradient(160deg, ${color}22, #f5f5f5 60%)` }}
      >
        <Card style={{ width: '100%', maxWidth: 400 }} styles={{ body: { padding: 24 } }}>
          <Flex vertical align="center" gap={8} style={{ marginBottom: 24, minHeight: 104 }}>
            {school ? (
              <>
                <SchoolLogo name={school.name} logoUrl={school.logoUrl} color={color} size={64} />
                <Typography.Title level={4} style={{ margin: 0, textAlign: 'center' }}>
                  {school.name}
                </Typography.Title>
              </>
            ) : (
              <>
                <BankOutlined style={{ fontSize: 48, color }} />
                <Typography.Title level={4} style={{ margin: 0 }}>
                  Sign in to your school
                </Typography.Title>
              </>
            )}
          </Flex>

          {login.isError && (
            <Alert type="error" showIcon title={errorMessage(login.error)} style={{ marginBottom: 16 }} />
          )}

          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            initialValues={{ schoolCode: initialCode }}
            onFinish={(values) => login.mutate(values)}
            disabled={login.isPending}
          >
            <Form.Item
              name="schoolCode"
              label="School code"
              rules={[{ required: true, whitespace: true, message: 'School code is required' }, { max: 40 }]}
              help={
                schoolCode.length >= 2 && isNotFound(branding.error) ? 'No school found with this code' : undefined
              }
            >
              <Input prefix={<BankOutlined />} size="large" autoCapitalize="none" autoComplete="organization" />
            </Form.Item>
            <Form.Item
              name="username"
              label="Username"
              rules={[{ required: true, whitespace: true, message: 'Username is required' }, { max: 60 }]}
            >
              <Input prefix={<UserOutlined />} size="large" autoCapitalize="none" autoComplete="username" />
            </Form.Item>
            <Form.Item
              name="password"
              label="Password"
              rules={[{ required: true, message: 'Password is required' }, { max: 100 }]}
            >
              <Input.Password prefix={<LockOutlined />} size="large" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" block loading={login.isPending}>
              Sign in
            </Button>
          </Form>
        </Card>
      </Flex>
    </ConfigProvider>
  )
}
