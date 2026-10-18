import { Card, Empty, Typography } from 'antd'
import { useAuth } from '../../auth/useAuth'

export function DashboardPage() {
  const { me } = useAuth()
  return (
    <>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Welcome, {me?.user.fullName}
      </Typography.Title>
      <Card>
        <Empty description="Your school dashboard will appear here." />
      </Card>
    </>
  )
}
