import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { getApiErrorMessage } from '../utils/validation';

const permissionGroups = [
  { value: 'BILLING', label: 'Billing', children: [
    { value: 'BILLING_CHECKOUT', label: 'Checkout' },
    { value: 'BILLING_INVOICES', label: 'Invoices' },
    { value: 'BILLING_ORDERS', label: 'Orders' }
  ] },
  { value: 'PRODUCTS', label: 'Inventory', children: [
    { value: 'PRODUCTS_LIST', label: 'Products' },
    { value: 'PRODUCTS_CATEGORIES', label: 'Categories' }
  ] },
  { value: 'CUSTOMERS', label: 'Customers', children: [
    { value: 'CUSTOMERS_DASHBOARD', label: 'Dashboard' },
    { value: 'CUSTOMERS_OVERVIEW', label: 'Customer Info' },
    { value: 'CUSTOMERS_SEARCH_ACTIVITY', label: 'Search Activity' },
    { value: 'CUSTOMERS_LOGIN_HISTORY', label: 'Login History' },
    { value: 'CUSTOMERS_SUPPORT_CHAT', label: 'Support Chat' },
    { value: 'CUSTOMERS_AI_INSIGHTS', label: 'AI Insights' }
  ] },
  { value: 'MARKETING_AUTOMATION', label: 'Campaign Studio', children: [
    { value: 'CAMPAIGNS_DASHBOARD', label: 'Dashboard' },
    { value: 'CAMPAIGNS_LIST', label: 'Campaign List' },
    { value: 'CAMPAIGNS_CREATE', label: 'Create Campaign' },
    { value: 'CAMPAIGNS_TEMPLATES', label: 'Templates' },
    { value: 'CAMPAIGNS_AUDIENCE', label: 'Audience' },
    { value: 'CAMPAIGNS_OFFERS', label: 'Offers' },
    { value: 'CAMPAIGNS_APPROVAL', label: 'Approval Queue' }
  ] },
  { value: 'REPORTS', label: 'Reports', children: [
    { value: 'REPORTS_DASHBOARD', label: 'Dashboard' },
    { value: 'REPORTS_SALES', label: 'Sales reports' },
    { value: 'REPORTS_PAYMENTS', label: 'Razorpay diagnostics' }
  ] },
  { value: 'RECEIPT_SETTINGS', label: 'Store Configuration', children: [
    { value: 'RECEIPT_SETTINGS_BUSINESS', label: 'Business details' },
    { value: 'RECEIPT_SETTINGS_THEME', label: 'Theme and media' },
    { value: 'RECEIPT_SETTINGS_SOCIAL', label: 'Social links' },
    { value: 'RECEIPT_SETTINGS_META_CATALOG', label: 'Meta catalog' }
  ] },
  { value: 'SITE_INTERACTIONS', label: 'Website Activity', children: [] },
  { value: 'SALESPERSON_SALES', label: 'Salesperson Performance', children: [] },
  { value: 'USER_MANAGEMENT', label: 'Users', children: [] }
];

const permissionOptions = permissionGroups.flatMap((group) => [group, ...(group.children || [])]);
const childToParentPermission = Object.fromEntries(
  permissionGroups.flatMap((group) => (group.children || []).map((child) => [child.value, group.value]))
);

const permissionLabel = (permission) =>
  permissionOptions.find((option) => option.value === permission)?.label || permission.replaceAll('_', ' ');

const expandPermissionsForForm = (permissions = []) => {
  const selected = new Set(permissions);
  permissionGroups.forEach((group) => {
    if (selected.has(group.value)) {
      (group.children || []).forEach((child) => selected.add(child.value));
    }
  });
  return Array.from(selected);
};

const blankForm = {
  username: '',
  password: '',
  displayName: '',
  role: 'STAFF',
  enabled: true,
  salesPerson: false,
  permissions: ['BILLING']
};

const previewUsersPage = {
  items: [
    {
      id: 'preview-owner',
      displayName: 'Store Owner',
      username: 'owner',
      role: 'OWNER',
      enabled: true,
      salesPerson: false,
      permissions: permissionOptions.map((option) => option.value)
    },
    {
      id: 'preview-billing',
      displayName: 'Billing Staff',
      username: 'billing',
      role: 'STAFF',
      enabled: true,
      salesPerson: true,
      permissions: ['BILLING', 'CUSTOMERS']
    }
  ],
  page: 0,
  totalPages: 1,
  totalItems: 2,
  hasNext: false,
  hasPrevious: false
};

function isLocalUsersPreviewEnabled() {
  if (!import.meta.env.DEV || typeof window === 'undefined') return false;
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
}

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
      if (isLocalUsersPreviewEnabled()) {
        setUsersPage(previewUsersPage);
        setError('');
        return;
      }
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
      const parentPermission = childToParentPermission[permission];
      const nextPermissions = current.permissions.includes(permission)
        ? current.permissions.filter((item) => item !== permission)
        : [...current.permissions, permission];

      return {
        ...current,
        permissions: parentPermission
          ? nextPermissions.filter((item) => item !== parentPermission)
          : nextPermissions
      };
    });
  };

  const togglePermissionGroup = (group) => {
    const groupValues = [group.value, ...(group.children || []).map((child) => child.value)];
    const allSelected = groupValues.every((permission) => form.permissions.includes(permission));
    setForm((current) => ({
      ...current,
      permissions: allSelected
        ? current.permissions.filter((permission) => !groupValues.includes(permission))
        : Array.from(new Set([...current.permissions, ...groupValues]))
    }));
  };

  const normalizePermissionsForSave = () => {
    const selected = new Set(form.permissions);
    permissionGroups.forEach((group) => {
      const childValues = (group.children || []).map((child) => child.value);
      if (!childValues.length) return;
      const selectedChildren = childValues.filter((permission) => selected.has(permission));
      if (selectedChildren.length === childValues.length) {
        selected.add(group.value);
      } else {
        selected.delete(group.value);
      }
    });
    return Array.from(selected);
  };

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');

    const permissions = normalizePermissionsForSave();
    if (permissions.length === 0) {
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
        permissions
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
      if (isLocalUsersPreviewEnabled()) {
        setSuccess(editingId ? 'Preview account updated locally.' : 'Preview account created locally.');
        resetForm();
        return;
      }
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
      permissions: expandPermissionsForForm(user.permissions || [])
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

            <div className="permission-group-grid">
              {permissionGroups.map((group) => {
                const childValues = (group.children || []).map((child) => child.value);
                const selectedChildren = childValues.filter((permission) => form.permissions.includes(permission));
                const groupSelected = childValues.length
                  ? selectedChildren.length === childValues.length
                  : form.permissions.includes(group.value);

                return (
                  <section key={group.value} className="permission-group-card">
                    <label className="toggle-field permission-group-head">
                      <input
                        type="checkbox"
                        checked={groupSelected}
                        onChange={() => childValues.length ? togglePermissionGroup(group) : togglePermission(group.value)}
                      />
                      <span>{group.label}</span>
                      {childValues.length ? <em>{selectedChildren.length}/{childValues.length}</em> : null}
                    </label>
                    {group.children?.length ? (
                      <div className="permission-submenu-grid">
                        {group.children.map((option) => (
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
                    ) : null}
                  </section>
                );
              })}
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
