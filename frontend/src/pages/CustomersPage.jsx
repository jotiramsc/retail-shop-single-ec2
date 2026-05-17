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
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerDetails, setCustomerDetails] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);
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

  const loadCustomerDetails = async (customer) => {
    setSelectedCustomer(customer);
    setSelectedMobile(customer.mobile);
    setError('');
    setLoadingDetails(true);
    try {
      const details = await retailService.getCustomerDetails(customer.id);
      setCustomerDetails(details);
      try {
        setHistory(await retailService.getCustomerHistory(details.mobile || customer.mobile));
      } catch {
        setHistory([]);
      }
    } catch (requestError) {
      setCustomerDetails(null);
      setHistory([]);
      setError(getApiErrorMessage(requestError, 'Unable to load customer details.'));
    } finally {
      setLoadingDetails(false);
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
              { key: 'email', label: 'Email', render: (row) => row.email || '—' },
              {
                key: 'action',
                label: 'Details',
                render: (row) => (
                  <button className="ghost-btn" onClick={() => loadCustomerDetails(row)}>
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
          title="Customer details"
          subtitle={selectedMobile ? `Showing profile for ${selectedMobile}` : 'Choose a customer to load profile, orders, and invoice history.'}
        >
          {loadingDetails ? <p className="field-note">Loading customer details...</p> : null}
          {customerDetails ? (
            <div className="customer-detail-stack">
              <div className="customer-detail-card">
                <div>
                  <span>Name</span>
                  <strong>{customerDetails.name || selectedCustomer?.name || '—'}</strong>
                </div>
                <div>
                  <span>Mobile</span>
                  <strong>{customerDetails.mobile || '—'}</strong>
                </div>
                <div>
                  <span>Email</span>
                  <strong>{customerDetails.email || '—'}</strong>
                </div>
                <div className="customer-detail-address">
                  <span>Address</span>
                  <strong>{customerDetails.fullAddress || 'No address saved'}</strong>
                </div>
              </div>

              <div className="customer-detail-metrics">
                <article className="metric-card">
                  <span>Total orders</span>
                  <strong>{customerDetails.totalOrders || 0}</strong>
                </article>
                <article className="metric-card">
                  <span>Pending orders</span>
                  <strong>{customerDetails.pendingOrders || 0}</strong>
                </article>
                <article className="metric-card">
                  <span>Total spent</span>
                  <strong>{currency(customerDetails.totalSpent || 0)}</strong>
                </article>
              </div>

              <div>
                <h3 className="section-subtitle">Order history</h3>
                <DataTable
                  columns={[
                    { key: 'orderNumber', label: 'Order' },
                    { key: 'status', label: 'Status' },
                    { key: 'amount', label: 'Amount', render: (row) => currency(row.amount) },
                    { key: 'createdAt', label: 'Ordered On', render: (row) => formatDate(row.createdAt) }
                  ]}
                  rows={customerDetails.orderHistory || []}
                  emptyMessage="No orders found."
                />
              </div>

              <div>
                <h3 className="section-subtitle">Purchase history</h3>
                <DataTable
                  columns={[
                    { key: 'invoiceNumber', label: 'Invoice' },
                    { key: 'finalAmount', label: 'Amount', render: (row) => currency(row.finalAmount) },
                    { key: 'createdAt', label: 'Purchased On', render: (row) => formatDate(row.createdAt) }
                  ]}
                  rows={history}
                  emptyMessage="No invoices found."
                />
              </div>
            </div>
          ) : (
            <p className="field-note">Select View on a customer row to see their full profile.</p>
          )}
        </Panel>
      </div>
    </div>
  );
}
