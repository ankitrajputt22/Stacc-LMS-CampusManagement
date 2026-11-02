/**
 * How a course text is shown: PLAIN exactly as typed with its line breaks, FORMATTED with Stacc's small formatting set
 * (headings, lists, links, code, tables, and simple maths, written as Markdown). Never HTML. Texts written before
 * formatting existed are PLAIN.
 */
export type TextFormat = 'PLAIN' | 'FORMATTED'
