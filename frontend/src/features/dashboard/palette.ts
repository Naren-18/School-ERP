/**
 * Chart colours and number formats shared by the dashboard charts.
 * Multi-series charts and donuts take the categorical slots in this fixed order
 * (validated for colour-blind separation on white) and always show a legend
 * with the values, since some slots are below 3:1 contrast.
 */
export const CATEGORICAL = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#6250d6']

/** Folded slices ("Other"). */
export const NEUTRAL = '#a3a29c'

export const compactInr = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 })

/** ₹ in Indian short form: ₹45.2K, ₹3.4L, ₹1.2Cr. */
export const formatINRCompact = (v: number) => (v === 0 ? '₹0' : `₹${compactInr.format(v)}`)
