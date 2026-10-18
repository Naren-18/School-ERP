import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Flex, Grid, Layout, Menu, Typography, theme } from 'antd'
import { LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/useAuth'
import { SchoolLogo } from '../components/SchoolLogo'
import { MENU } from './menu'

const ROLE_LABELS: Record<string, string> = {
  PRINCIPAL: 'Principal',
  ADMIN: 'Admin',
  ACCOUNTANT: 'Accountant',
  TEACHER: 'Teacher',
}

export function AppShell() {
  const { me, hasAnyRole, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg
  const [collapsed, setCollapsed] = useState(true)
  const { token } = theme.useToken()

  if (!me) {
    return null
  }

  const items = MENU.filter((entry) => hasAnyRole(entry.roles)).map((entry) => ({
    key: entry.path,
    icon: entry.icon,
    label: entry.label,
  }))

  const selected = MENU.filter((e) => e.path === '/' ? location.pathname === '/' : location.pathname.startsWith(e.path))
    .map((e) => e.path)

  const logout = () => {
    signOut()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider
        theme="light"
        width={220}
        collapsedWidth={0}
        collapsed={isMobile ? collapsed : false}
        trigger={null}
        style={{
          borderRight: `1px solid ${token.colorBorderSecondary}`,
          ...(isMobile ? { position: 'fixed', height: '100vh', zIndex: 20, left: 0, top: 0 } : {}),
        }}
      >
        <Flex align="center" gap={10} style={{ height: 64, padding: '0 16px' }}>
          <SchoolLogo name={me.school.name} logoUrl={me.school.logoUrl} color={me.school.primaryColor} size={32} />
          <Typography.Text strong ellipsis style={{ flex: 1 }}>
            {me.school.name}
          </Typography.Text>
        </Flex>
        <Menu
          mode="inline"
          items={items}
          selectedKeys={selected}
          style={{ borderInlineEnd: 'none' }}
          onClick={({ key }) => {
            navigate(key)
            setCollapsed(true)
          }}
        />
      </Layout.Sider>
      {isMobile && !collapsed && (
        <div
          onClick={() => setCollapsed(true)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.35)', zIndex: 10 }}
        />
      )}
      <Layout>
        <Layout.Header
          style={{
            background: token.colorBgContainer,
            borderBottom: `3px solid ${token.colorPrimary}`,
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
          }}
        >
          {isMobile && (
            <>
              <Button type="text" icon={<MenuOutlined />} onClick={() => setCollapsed(false)} aria-label="Open menu" />
              <SchoolLogo name={me.school.name} logoUrl={me.school.logoUrl} color={me.school.primaryColor} size={32} />
            </>
          )}
          <Typography.Title level={5} ellipsis style={{ margin: 0, flex: 1, minWidth: 0 }}>
            {me.school.name}
          </Typography.Title>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'who',
                  disabled: true,
                  label: (
                    <Flex vertical>
                      <Typography.Text strong>{me.user.fullName}</Typography.Text>
                      <Typography.Text type="secondary">
                        {me.roles.map((role) => ROLE_LABELS[role] ?? role).join(', ')}
                      </Typography.Text>
                    </Flex>
                  ),
                },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: 'Sign out', onClick: logout },
              ],
            }}
          >
            <Button type="text" style={{ height: 48, paddingInline: 8 }}>
              <Flex align="center" gap={8}>
                <Avatar size="small" icon={<UserOutlined />} style={{ background: token.colorPrimary }} />
                {!isMobile && <Typography.Text>{me.user.fullName}</Typography.Text>}
              </Flex>
            </Button>
          </Dropdown>
        </Layout.Header>
        <Layout.Content style={{ padding: 16 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
