export default function DataTable({
  columns,
  rows,
  emptyMessage = 'No records found.',
  pagination = null,
  onPageChange = null
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="empty-cell">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row, index) => (
              <tr key={row.id || row.invoiceId || row.productId || index}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
      {pagination && pagination.totalPages > 0 ? (
        <div className="table-pagination">
          <span>
            Page {pagination.page + 1} of {pagination.totalPages}
            {' '}
            <small>({pagination.totalItems} total)</small>
          </span>
          <div className="table-pagination-actions">
            <button
              type="button"
              className="ghost-btn compact-btn"
              onClick={() => onPageChange?.(pagination.page - 1)}
              disabled={!pagination.hasPrevious}
            >
              Previous
            </button>
            <button
              type="button"
              className="ghost-btn compact-btn"
              onClick={() => onPageChange?.(pagination.page + 1)}
              disabled={!pagination.hasNext}
            >
              Next
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
