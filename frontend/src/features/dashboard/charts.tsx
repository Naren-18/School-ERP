import type { ReactNode } from 'react'
import dayjs from 'dayjs'
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { ClassCount, DayPercent } from '../../api/dashboard'
import { formatINR } from '../../utils/format'

/**
 * Single-series charts, so one validated hue (blue, 3:1+ on white) and no
 * legend: the card title names the series. Thin marks, a hairline recessive
 * grid, text in ink colours, and a tooltip on hover.
 */
const SERIES = '#2a78d6'
const GRID = '#ececec'
const INK_SECONDARY = '#52514e'
const SURFACE = '#ffffff'
const tick = { fill: INK_SECONDARY, fontSize: 12 }

const compactInr = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 })

function TooltipBox({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <div
      style={{
        background: SURFACE,
        border: `1px solid ${GRID}`,
        borderRadius: 8,
        padding: '6px 10px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
        fontSize: 13,
      }}
    >
      <div style={{ color: INK_SECONDARY }}>{title}</div>
      <div style={{ fontWeight: 600, color: '#111' }}>{children}</div>
    </div>
  )
}

/** Attendance % per school day: a 2px line with ringed points. */
export function AttendanceTrendChart({ data }: { data: DayPercent[] }) {
  const rows = data.filter((d) => d.percentage !== null)
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={rows} margin={{ top: 8, right: 24, bottom: 0, left: -12 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="date" tickFormatter={(d: string) => dayjs(d).format('DD-MM')} tick={tick} tickLine={false} axisLine={{ stroke: GRID }} minTickGap={16} />
        <YAxis domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(v: number) => `${v}%`} tick={tick} tickLine={false} axisLine={false} />
        <Tooltip
          cursor={{ stroke: GRID, strokeWidth: 1 }}
          content={({ active, payload }) => {
            const p = payload?.[0]?.payload as DayPercent | undefined
            return active && p ? (
              <TooltipBox title={dayjs(p.date).format('ddd, DD-MM-YYYY')}>
                {p.percentage}% present · {p.marked} marked
              </TooltipBox>
            ) : null
          }}
        />
        <Line
          type="monotone"
          dataKey="percentage"
          stroke={SERIES}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
          dot={{ r: 4, fill: SERIES, stroke: SURFACE, strokeWidth: 2 }}
          activeDot={{ r: 5, fill: SERIES, stroke: SURFACE, strokeWidth: 2 }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

/** Money collected per day: columns from one baseline, rounded at the data end. */
export function CollectionChart({ data }: { data: { date: string; amount: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: -4 }} barCategoryGap={2}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="date" tickFormatter={(d: string) => dayjs(d).format('DD-MM')} tick={tick} tickLine={false} axisLine={{ stroke: GRID }} minTickGap={16} />
        <YAxis tickFormatter={(v: number) => (v === 0 ? '0' : `₹${compactInr.format(v)}`)} tick={tick} tickLine={false} axisLine={false} width={56} />
        <Tooltip
          cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          content={({ active, payload }) => {
            const p = payload?.[0]?.payload as { date: string; amount: number } | undefined
            return active && p ? <TooltipBox title={dayjs(p.date).format('ddd, DD-MM-YYYY')}>{formatINR(p.amount)}</TooltipBox> : null
          }}
        />
        <Bar dataKey="amount" fill={SERIES} maxBarSize={24} radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Active students per class: horizontal bars so long class names stay readable. */
export function StudentsByClassChart({ data }: { data: ClassCount[] }) {
  const height = Math.max(160, data.length * 30 + 24)
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 28, bottom: 0, left: 0 }} barCategoryGap={4}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={tick} tickLine={false} axisLine={false} />
        <YAxis type="category" dataKey="className" tick={tick} tickLine={false} axisLine={{ stroke: GRID }} width={84} />
        <Tooltip
          cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          content={({ active, payload }) => {
            const p = payload?.[0]?.payload as ClassCount | undefined
            return active && p ? <TooltipBox title={p.className}>{p.students} students</TooltipBox> : null
          }}
        />
        <Bar dataKey="students" fill={SERIES} maxBarSize={18} radius={[0, 4, 4, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
