/**
 * 纯前端 CSV 导出工具。
 *
 * - 自动转义逗号、引号、换行
 * - 写入 UTF-8 BOM，确保 Excel 正确识别中文不乱码
 * - 通过 Blob + a[download] 触发浏览器下载，无需后端接口
 */
export interface CsvColumn {
  key: string;
  label: string;
}

export function exportToCsv(
  filename: string,
  columns: CsvColumn[],
  rows: Array<Record<string, unknown>>
): void {
  const escape = (val: unknown): string => {
    const s = val === null || val === undefined ? '' : String(val);
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  };

  const header = columns.map((c) => escape(c.label)).join(',');
  const body = rows
    .map((row) => columns.map((c) => escape(row[c.key])).join(','))
    .join('\n');
  // ﻿ = UTF-8 BOM，让 Excel 以 UTF-8 打开避免中文乱码
  const csv = '﻿' + header + '\n' + body;

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
