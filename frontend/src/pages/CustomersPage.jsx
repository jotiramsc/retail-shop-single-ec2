import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage, isValidMobile } from '../utils/validation';

const blankCustomer = {
  name: '',
  mobile: ''
};

export default function CustomersPage() {
  const [customersPage, setCustomersPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [history, setHistory] = useState([]);
  const [selectedMobile, setSelectedMobile] = useState('');
  const [form, setForm] = useState(blankCustomer);
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadCustomers = async (page = 0) => {
    try {
      setCustomersPage(await retailService.getCustomers({ page, size: 10 }));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load customers.'));
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  const loadHistory = async (mobile) => {
    setSelectedMobile(mobile);
    setError('');
    try {
      setHistory(await retailService.getCustomerHistory(mobile));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load purchase history.'));
    }
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    if (!isValidMobile(form.mobile)) {
      setError('Enter a valid mobile number with 10 to 15 digits.');
      return;
    }
    try {
      await retailService.createCustomer(form);
      setForm(blankCustomer);
      setSuccess('Customer created successfully.');
      loadCustomers();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to create customer.'));
    }
  };

  const customers = customersPage.items || [];

  const filteredCustomers = customers.filter((customer) => {
    const haystack = `${customer.name} ${customer.mobile}`.toLowerCase();
    return haystack.includes(search.toLowerCase());
  });

  return (
    <div className="page">
      <PageHeader
        eyebrow="Customers"
        title="Customer memory and purchase history"
        description="Look up repeat buyers quickly, keep mobile-first records, and see their invoice trail without altering past sales."
      />

      <div className="two-column">
        <Panel title="Customer CRM" subtitle="Create customers and filter the list before drilling into history.">
          <form className="form-grid" onSubmit={handleCreate}>
            <input
              placeholder="Customer name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            <input
              placeholder="Customer mobile"
              value={form.mobile}
              onChange={(e) => setForm({ ...form, mobile: e.target.value })}
              inputMode="numeric"
              required
            />
            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}
            <button className="primary-btn" type="submit">Add Customer</button>
          </form>

          <div className="toolbar-row">
            <input
              placeholder="Search by name or mobile"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <DataTable
            columns={[
              { key: 'name', label: 'Name' },
              { key: 'mobile', label: 'Mobile' },
              {
                key: 'action',
                label: 'History',
                render: (row) => (
                  <button className="ghost-btn" onClick={() => loadHistory(row.mobile)}>
                    View
                  </button>
                )
              }
            ]}
            rows={filteredCustomers}
            pagination={customersPage}
            onPageChange={loadCustomers}
          />
        </Panel>

        <Panel
          title="Purchase history"
          subtitle={selectedMobile ? `Showing invoices for ${selectedMobile}` : 'Choose a customer to load invoice history.'}
        >
          <DataTable
            columns={[
              { key: 'invoiceNumber', label: 'Invoice' },
              { key: 'finalAmount', label: 'Amount', render: (row) => currency(row.finalAmount) },
              { key: 'createdAt', label: 'Purchased On', render: (row) => formatDate(row.createdAt) }
            ]}
            rows={history}
          />
        </Panel>
      </div>
    </div>
  );
}
