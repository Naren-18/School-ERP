import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Avatar } from 'antd'
import { studentsApi } from '../../api/students'

interface Props {
  studentId: number
  name: string
  hasPhoto: boolean
  /** Changes when a new photo is uploaded, so the cached image is refreshed. */
  version?: number
  size?: number
}

/**
 * Student photos are private, so they are fetched with the auth header and
 * shown from an object URL (a plain <img src> could not send the token).
 */
export function StudentPhoto({ studentId, name, hasPhoto, version = 0, size = 40 }: Props) {
  const { data: blob } = useQuery({
    queryKey: ['student-photo', studentId, version],
    queryFn: () => studentsApi.photo(studentId),
    enabled: hasPhoto,
    staleTime: Infinity,
  })
  const url = useMemo(() => (blob ? URL.createObjectURL(blob) : undefined), [blob])
  useEffect(() => () => (url ? URL.revokeObjectURL(url) : undefined), [url])

  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')
  return (
    <Avatar size={size} src={url} style={{ flexShrink: 0 }}>
      {initials}
    </Avatar>
  )
}
