import type { ReactNode } from 'react'
import dayjs from 'dayjs'
import {
  Bar, BarChart, CartesianGrid, Cell, LabelList, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { ClassCount, DayPercent } from '../../api/dashboard'
import { formatINR } from '../../utils/format'
import { CATEGORICAL, compactInr } from './palette'

/**
 * Single-series charts use one validated hue (blue, 3:1+ on white) and no
 * legend: the card title names the series. Multi-series charts and donuts use
 * the categorical slots (see palette.ts) with a legend. Thin marks, a hairline
 * recessive grid, text in ink colours, and a tooltip on hover.
 */
const SERIES = CATEGORICAL[0]
const GRID = '#ececec'
const INK_SECONDARY = '#52514e'
const SURFACE = '#ffffff'
const tick = { fill: INK_SECONDARY, fontSize: 12 }

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

/** Money collected per month of the year: same columns as the daily chart, month labels. */
export function MonthlyCollectionChart({ data }: { data: { month: string; amount: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: -4 }} barCategoryGap={2}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="month" tickFormatter={(m: string) => dayjs(m).format('MMM')} tick={tick} tickLine={false} axisLine={{ stroke: GRID }} interval={0} minTickGap={4} />
        <YAxis tickFormatter={(v: number) => (v === 0 ? '0' : `₹${compactInr.format(v)}`)} tick={tick} tickLine={false} axisLine={false} width={56} />
        <Tooltip
          cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          content={({ active, payload }) => {
            const p = payload?.[0]?.payload as { month: string; amount: number } | undefined
            return active && p ? <TooltipBox title={dayjs(p.month).format('MMMM YYYY')}>{formatINR(p.amount)}</TooltipBox> : null
          }}
        />
        <Bar dataKey="amount" fill={SERIES} maxBarSize={32} radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Swatch + label for a legend row; the text stays in ink colours. */
function Swatch({ color }: { color: string }) {
  return <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 3, background: color, flex: 'none' }} />
}

/** Legend for multi-series bars: always shown, so identity is never colour alone. */
export function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 13, color: INK_SECONDARY, marginBottom: 8 }}>
      {items.map((i) => (
        <span key={i.label} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <Swatch color={i.color} />
          {i.label}
        </span>
      ))}
    </div>
  )
}

/**
 * Boys and girls per class: stacked horizontal bars, a 2px surface gap between
 * the two segments, the outer end rounded. "Other" appears only when recorded.
 */
export function StudentsByClassChart({ data }: { data: ClassCount[] }) {
  const rows = data.map((c) => ({ ...c, other: c.students - c.boys - c.girls }))
  const hasOther = rows.some((r) => r.other > 0)
  const height = Math.max(160, rows.length * 30 + 24)
  const series = [
    { key: 'boys', label: 'Boys', color: CATEGORICAL[0] },
    { key: 'girls', label: 'Girls', color: CATEGORICAL[1] },
    ...(hasOther ? [{ key: 'other', label: 'Other', color: CATEGORICAL[2] }] : []),
  ]
  return (
    <>
      <Legend items={series} />
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 28, bottom: 0, left: 0 }} barCategoryGap={4}>
          <CartesianGrid stroke={GRID} horizontal={false} />
          <XAxis type="number" allowDecimals={false} tick={tick} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="className" tick={tick} tickLine={false} axisLine={{ stroke: GRID }} width={84} />
          <Tooltip
            cursor={{ fill: 'rgba(0,0,0,0.04)' }}
            content={({ active, payload }) => {
              const p = payload?.[0]?.payload as (typeof rows)[number] | undefined
              return active && p ? (
                <TooltipBox title={`${p.className} · ${p.students} students`}>
                  {p.boys} boys · {p.girls} girls{p.other > 0 && ` · ${p.other} other`}
                </TooltipBox>
              ) : null
            }}
          />
          {series.map((s, i) => (
            <Bar
              key={s.key}
              dataKey={s.key}
              stackId="students"
              fill={s.color}
              stroke={SURFACE}
              strokeWidth={1}
              maxBarSize={18}
              radius={i === series.length - 1 ? [0, 4, 4, 0] : 0}
              isAnimationActive={false}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </>
  )
}

/** One percentage per category (class, subject): horizontal bars on a fixed 0–100% scale. */
export function PercentBarChart<T extends { percentage: number | null }>({
  data, labelKey, tooltip,
}: {
  data: T[]
  labelKey: keyof T & string
  tooltip: (row: T) => { title: ReactNode; body: ReactNode }
}) {
  const height = Math.max(140, data.length * 30 + 32)
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 44, bottom: 0, left: 0 }} barCategoryGap={4}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={(v: number) => `${v}%`} tick={tick} tickLine={false} axisLine={false} />
        <YAxis type="category" dataKey={labelKey as string} tick={tick} tickLine={false} axisLine={{ stroke: GRID }} width={96} />
        <Tooltip
          cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          content={({ active, payload }) => {
            const p = payload?.[0]?.payload as T | undefined
            if (!active || !p) return null
            const t = tooltip(p)
            return <TooltipBox title={t.title}>{t.body}</TooltipBox>
          }}
        />
        <Bar dataKey="percentage" fill={SERIES} maxBarSize={18} radius={[0, 4, 4, 0]} isAnimationActive={false}>
          <LabelList dataKey="percentage" position="right" formatter={(v) => (v == null ? '' : `${v}%`)} style={{ fill: INK_SECONDARY, fontSize: 12 }} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

export interface Slice {
  key: string
  label: string
  value: number
  color: string
}

/**
 * Part-to-whole at a glance (≤ 6 slices): a ring with the headline in the
 * middle and a legend carrying each slice's value and share, so the colours
 * never carry meaning alone. Slices are separated by a 2px surface gap.
 */
export function DonutChart({
  slices, centerValue, centerLabel, format = (v) => String(v),
}: {
  slices: Slice[]
  centerValue: ReactNode
  centerLabel: ReactNode
  format?: (v: number) => string
}) {
  const total = slices.reduce((s, x) => s + x.value, 0)
  const shown = slices.filter((s) => s.value > 0)
  const share = (v: number) => (total > 0 ? `${Math.round((v * 100) / total)}%` : '—')
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap', justifyContent: 'center' }}>
      <div style={{ position: 'relative', width: 168, height: 168, flex: 'none' }}>
        <PieChart width={168} height={168}>
          <Pie
            data={shown}
            dataKey="value"
            nameKey="label"
            innerRadius={56}
            outerRadius={82}
            startAngle={90}
            endAngle={-270}
            stroke={SURFACE}
            strokeWidth={2}
            isAnimationActive={false}
          >
            {shown.map((s) => (
              <Cell key={s.key} fill={s.color} />
            ))}
          </Pie>
          <Tooltip
            content={({ active, payload }) => {
              const p = payload?.[0]?.payload as Slice | undefined
              return active && p ? (
                <TooltipBox title={p.label}>
                  {format(p.value)} · {share(p.value)}
                </TooltipBox>
              ) : null
            }}
          />
        </PieChart>
        <div
          style={{
            position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', pointerEvents: 'none', textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 20, fontWeight: 600, color: '#111', fontVariantNumeric: 'tabular-nums', lineHeight: 1.2 }}>{centerValue}</div>
          <div style={{ fontSize: 12, color: INK_SECONDARY }}>{centerLabel}</div>
        </div>
      </div>
      <div style={{ flex: '1 1 150px', minWidth: 150, maxWidth: 280, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {slices.map((s) => (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <Swatch color={s.color} />
            <span style={{ flex: 1, minWidth: 0, color: INK_SECONDARY }}>{s.label}</span>
            <span style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums', color: '#111' }}>{format(s.value)}</span>
            <span style={{ width: 36, textAlign: 'right', color: INK_SECONDARY, fontVariantNumeric: 'tabular-nums' }}>{share(s.value)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
