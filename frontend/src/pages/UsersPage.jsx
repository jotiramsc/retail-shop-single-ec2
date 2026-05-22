import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { getApiErrorMessage } from '../utils/validation';

const permissionOptions = [
  { value: 'BILLING', label: 'Billing' },
  { value: 'PRODUCTS', label: 'Inventory' },
  { value: 'CUSTOMERS', label: 'Customers' },
  { value: 'OFFERS', label: 'Offers' },
  { value: 'MARKETING_AUTOMATION', label: 'Campaign Studio' },
  { value: 'REPORTS', label: 'Reports' },
  { value: 'SITE_INTERACTIONS', label: 'Website Activity' },
  { value: 'SALESPERSON_SALES', label: 'Salesperson Performance' },
  { value: 'RECEIPT_SETTINGS', label: 'Store Configuration' },
  { value: 'USER_MANAGEMENT', label: 'Users' }
];

const permissionLabel = (permission) =>
  permissionOptions.find((option) => option.value === permission)?.label || permission.replaceAll('_', ' ');

const blankForm = {
  username: '',
  password: '',
  displayName: '',
  role: 'STAFF',
  enabled: true,
  salesPerson: false,
  permissions: ['BILLING']
};

export default function UsersPage() {
  const [usersPage, setUsersPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [form, setForm] = useState(blankForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadUsers = async (page = 0) => {
    try {
      setUsersPage(await retailService.getUsers({ page, size: 10 }));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load users.'));
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const resetForm = () => {
    setForm(blankForm);
    setEditingId(null);
  };

  const togglePermission = (permission) => {
    setForm((current) => {
      const nextPermissions = current.permissions.includes(permission)
        ? current.permissions.filter((item) => item !== permission)
        : [...current.permissions, permission];

      return {
        ...current,
        permissions: nextPermissions
      };
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');

    if (form.permissions.length === 0) {
      setError('Choose at least one menu access option.');
      return;
    }

    try {
      const payload = {
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        role: form.role,
        enabled: form.enabled,
        salesPerson: form.salesPerson,
        permissions: form.permissions
      };

      if (editingId) {
        await retailService.updateUser(editingId, payload);
        setSuccess('User updated successfully.');
      } else {
        await retailService.createUser(payload);
        setSuccess('User created successfully.');
      }

      resetForm();
      loadUsers();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to save user.'));
    }
  };

  const startEdit = (user) => {
    setEditingId(user.id);
    setForm({
      username: user.username,
      password: '',
      displayName: user.displayName,
      role: user.role,
      enabled: Boolean(user.enabled),
      salesPerson: Boolean(user.salesPerson),
      permissions: user.permissions || []
    });
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Users"
        title="Team Accounts"
        description="Create staff logins, choose billing visibility, and assign the admin sections each person can open."
      />

      <div className="two-column">
        <Panel
          title={editingId ? 'Edit user' : 'Create user'}
          subtitle="Admins keep full access. Staff accounts can be limited to only the sections they need."
        >
          <form className="form-grid" onSubmit={submit}>
            <input
              placeholder="Display name"
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              required
            />
            <input
              placeholder="Username"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              required
            />
            <input
              type="password"
              placeholder={editingId ? 'New password (leave blank to keep current)' : 'Password'}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required={!editingId}
            />
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              <option value="STAFF">Staff</option>
              <option value="OWNER">Owner</option>
              <option value="ADMIN">Admin</option>
            </select>

            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.enabled)}
                onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              />
              <span>Allow login</span>
            </label>

            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.salesPerson)}
                onChange={(e) => setForm({ ...form, salesPerson: e.target.checked })}
              />
              <span>Show in billing salesperson list</span>
            </label>

            <div className="permission-grid">
              {permissionOptions.map((option) => (
                <label key={option.value} className="toggle-field">
                  <input
                    type="checkbox"
                    checked={form.permissions.includes(option.value)}
                    onChange={() => togglePermission(option.value)}
                  />
                  <span>{option.label}</span>
                </label>
              ))}
            </div>

            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}

            <div className="table-action-group">
              <button className="primary-btn" type="submit">
              {editingId ? 'Update Account' : 'Create Account'}
              </button>
              {editingId ? (
                <button type="button" className="ghost-btn" onClick={resetForm}>
                  Cancel
                </button>
              ) : null}
            </div>
          </form>
        </Panel>

        <Panel title="Team Accounts" subtitle="Click edit to change access, password, billing visibility, or enabled status.">
          <DataTable
            columns={[
              { key: 'displayName', label: 'Name' },
              { key: 'username', label: 'Username' },
              { key: 'role', label: 'Role' },
              { key: 'enabled', label: 'Status', render: (row) => row.enabled ? 'Active' : 'Disabled' },
              { key: 'salesPerson', label: 'Billing Salesperson', render: (row) => row.salesPerson ? 'Yes' : 'No' },
              {
                key: 'permissions',
                label: 'Menus',
                render: (row) => (
                  <div className="permission-chip-row">
                    {(row.permissions || []).map((permission) => (
                      <span key={permission} className="trust-chip small-chip">{permissionLabel(permission)}</span>
                    ))}
                  </div>
                )
              },
              {
                key: 'actions',
                label: 'Actions',
                render: (row) => (
                  <button type="button" className="ghost-btn compact-btn table-action-btn" onClick={() => startEdit(row)}>
                    Edit
                  </button>
                )
              }
            ]}
            rows={usersPage.items || []}
            pagination={usersPage}
            onPageChange={loadUsers}
          />
        </Panel>
      </div>
    </div>
  );
}
