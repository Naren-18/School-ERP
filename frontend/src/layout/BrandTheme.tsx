import { useEffect, type ReactNode } from 'react'
import { App as AntApp, ConfigProvider } from 'antd'
import enGB from 'antd/locale/en_GB'
import { useAuth } from '../auth/useAuth'

export const DEFAULT_PRIMARY = '#1e40af'

/** Themes the app with the signed-in school's primary colour and name. */
export function BrandTheme({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  const color = me?.school.primaryColor || DEFAULT_PRIMARY

  useEffect(() => {
    document.title = me ? `${me.school.name} · School ERP` : 'School ERP'
  }, [me])

  return (
    <ConfigProvider locale={enGB} theme={{ token: { colorPrimary: color, borderRadius: 8 } }}>
      <AntApp>{children}</AntApp>
    </ConfigProvider>
  )
}
