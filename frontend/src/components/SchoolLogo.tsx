import { Avatar } from 'antd'

interface Props {
  name: string
  logoUrl: string | null | undefined
  color?: string | null
  size?: number
}

/** School logo, or the school's initials when it has none. */
export function SchoolLogo({ name, logoUrl, color, size = 40 }: Props) {
  if (logoUrl) {
    return <Avatar src={logoUrl} alt={name} size={size} shape="square" style={{ flexShrink: 0 }} />
  }
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('')
  return (
    <Avatar size={size} shape="square" style={{ background: color ?? undefined, flexShrink: 0 }}>
      {initials}
    </Avatar>
  )
}
