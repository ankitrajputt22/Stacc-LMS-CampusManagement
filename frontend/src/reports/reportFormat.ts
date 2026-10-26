/** Counts as whole numbers with digit grouping, never with decimals. */
export const formatCount = (value: number) => Math.trunc(value).toLocaleString('en')
