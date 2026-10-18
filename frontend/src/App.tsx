import { Card, Flex, Typography } from 'antd'
import { Route, Routes } from 'react-router-dom'

function Placeholder() {
  return (
    <Flex align="center" justify="center" style={{ minHeight: '100vh', padding: 16, background: '#f5f5f5' }}>
      <Card style={{ maxWidth: 420, width: '100%', textAlign: 'center' }}>
        <Typography.Title level={3}>School ERP</Typography.Title>
        <Typography.Text type="secondary">Frontend is running. Login arrives in Phase 1.</Typography.Text>
      </Card>
    </Flex>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="*" element={<Placeholder />} />
    </Routes>
  )
}
