'use strict';

const $ = (id) => document.getElementById(id);
const state = { shifts: [], staff: [] };

// ---------------------------------------------------------------- helpers

/** fetch wrapper: parses JSON, throws Error(message) with .status on non-2xx. */
async function api(path, options = {}) {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.error || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return data;
}

const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const formatDate = (iso) =>
  new Date(iso + 'T00:00').toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });

let alertTimer;
function showAlert(type, message) {
  const el = $('alert');
  el.textContent = message;
  el.className = `alert ${type}`;
  clearTimeout(alertTimer);
  if (type === 'success') alertTimer = setTimeout(hideAlert, 4000);
}
function hideAlert() { $('alert').className = 'alert hidden'; }

const formValues = () => ({
  staffId: $('staffId').value,
  shiftDate: $('shiftDate').value,
  startTime: $('startTime').value,
  endTime: $('endTime').value,
});

// ----------------------------------------------------------------- loading

async function loadShifts() {
  state.shifts = await api('/shifts');
  renderShifts();
}

/** Loads all staff, or only those free in the chosen window if the checkbox is ticked. */
async function loadStaff() {
  const v = formValues();
  const filterByAvailability =
    $('onlyAvailable').checked && v.shiftDate && v.startTime && v.endTime && v.endTime > v.startTime;
  const qs = new URLSearchParams({ date: v.shiftDate, start: v.startTime, end: v.endTime });
  state.staff = await api(filterByAvailability ? `/staff/available?${qs}` : '/staff');
  renderStaffOptions();
}

// --------------------------------------------------------------- rendering

function renderStaffOptions() {
  const select = $('staffId');
  const previous = select.value;
  const groups = { DOCTOR: 'Doctors', NURSE: 'Nurses', ADMIN: 'Administrative' };

  let html = '<option value="">Select staff…</option>';
  for (const [role, label] of Object.entries(groups)) {
    const members = state.staff.filter((s) => s.role === role);
    if (!members.length) continue;
    html += `<optgroup label="${label}">` +
      members.map((s) => `<option value="${s.id}">${escapeHtml(s.name)} · ${escapeHtml(s.department)}</option>`).join('') +
      '</optgroup>';
  }
  select.innerHTML = html;
  select.value = state.staff.some((s) => String(s.id) === previous) ? previous : '';
}

function renderShifts() {
  const filter = $('statusFilter').value;
  const rows = state.shifts.filter((s) => !filter || s.status === filter);

  $('shiftRows').innerHTML = rows.map((s) => `
    <tr>
      <td>${formatDate(s.shiftDate)}</td>
      <td>${s.startTime}–${s.endTime}</td>
      <td><span class="staff-name">${escapeHtml(s.staffName)}</span>
          <span class="role">${escapeHtml(s.staffRole.toLowerCase())}</span></td>
      <td>${escapeHtml(s.department)}</td>
      <td><span class="badge ${s.status}">${s.status.toLowerCase()}</span></td>
      <td><div class="actions">
        ${s.status === 'SCHEDULED' ? `
          <button data-id="${s.id}" data-action="completed">Complete</button>
          <button data-id="${s.id}" data-action="cancelled">Cancel</button>` : ''}
        ${s.status === 'CANCELLED' ? `<button data-id="${s.id}" data-action="scheduled">Reopen</button>` : ''}
        <button class="danger" data-id="${s.id}" data-action="delete">Delete</button>
      </div></td>
    </tr>`).join('');

  $('emptyState').classList.toggle('hidden', rows.length > 0);

  const count = (st) => state.shifts.filter((s) => s.status === st).length;
  $('statTotal').textContent = state.shifts.length;
  $('statScheduled').textContent = count('SCHEDULED');
  $('statCompleted').textContent = count('COMPLETED');
  $('statCancelled').textContent = count('CANCELLED');
}

// ------------------------------------------------------------------ events

$('shiftForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = formValues();

  // Quick client-side checks; the server re-validates everything.
  if (!v.staffId || !v.shiftDate || !v.startTime || !v.endTime) {
    return showAlert('error', 'Please fill in all fields.');
  }
  if (v.endTime <= v.startTime) {
    return showAlert('error', 'End time must be after start time.');
  }

  $('submitBtn').disabled = true;
  try {
    await api('/shifts', { method: 'POST', body: JSON.stringify(v) });
    showAlert('success', 'Shift scheduled successfully.');
    await Promise.all([loadShifts(), loadStaff()]);
  } catch (err) {
    // 409 -> "Staff conflict detected! ..." comes straight from the backend
    showAlert('error', err.message);
  } finally {
    $('submitBtn').disabled = false;
  }
});

// Event delegation for row buttons (complete / cancel / reopen / delete)
$('shiftRows').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const { id, action } = btn.dataset;
  try {
    if (action === 'delete') {
      if (!confirm('Delete this shift permanently?')) return;
      await api(`/shifts/${id}`, { method: 'DELETE' });
    } else {
      await api(`/shifts/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: action }) });
    }
    hideAlert();
    await Promise.all([loadShifts(), loadStaff()]);
  } catch (err) {
    showAlert('error', err.message);   // e.g. reopening a shift that now clashes
  }
});

$('statusFilter').addEventListener('change', renderShifts);

// Refresh the staff dropdown when the time window or availability filter changes
['shiftDate', 'startTime', 'endTime', 'onlyAvailable'].forEach((id) =>
  $(id).addEventListener('change', () => loadStaff().catch((err) => showAlert('error', err.message))));

// -------------------------------------------------------------------- init

(async function init() {
  $('shiftDate').min = new Date().toLocaleDateString('en-CA');   // YYYY-MM-DD, local time
  try {
    await Promise.all([loadShifts(), loadStaff()]);
  } catch {
    showAlert('error', 'Cannot reach the server. Is the backend running?');
  }
})();
