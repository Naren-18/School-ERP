import type { ReactNode } from 'react'
import { Flex, Typography } from 'antd'

interface Props {
  title: string
  subtitle?: ReactNode
  extra?: ReactNode
}

export function PageHeader({ title, subtitle, extra }: Props) {
  return (
    <Flex justify="space-between" align="flex-start" gap={12} wrap style={{ marginBottom: 16 }}>
      <div style={{ minWidth: 0 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {subtitle && <Typography.Text type="secondary">{subtitle}</Typography.Text>}
      </div>
      {extra && (
        <Flex gap={8} wrap>
          {extra}
        </Flex>
      )}
    </Flex>
  )
}
