const API_BASE = 'http://localhost:8080';

const state = {
  token: localStorage.getItem('enrollgate_token') || null,
  userId: null,
  role: null,
};

function decodeJwt(token) {
  const payload = token.split('.')[1];
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(decodeURIComponent(escape(atob(base64))));
}

function showToast(message, isError = false) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.hidden = false;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => { toast.hidden = true; }, 4000);
}

async function apiFetch(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && state.token) headers['Authorization'] = 'Bearer ' + state.token;

  const res = await fetch(API_BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const message = (data && (data.message || data.error)) || `요청 실패 (HTTP ${res.status})`;
    throw new Error(message);
  }
  return data;
}

// ---- 인증 ----

function setSession(token) {
  state.token = token;
  localStorage.setItem('enrollgate_token', token);
  const claims = decodeJwt(token);
  state.userId = Number(claims.sub);
  state.role = claims.role;
  applySessionUi();
}

function clearSession() {
  state.token = null;
  state.userId = null;
  state.role = null;
  localStorage.removeItem('enrollgate_token');
  applySessionUi();
}

function applySessionUi() {
  const loggedIn = !!state.token;
  document.getElementById('auth-section').hidden = loggedIn;
  document.getElementById('app-section').hidden = !loggedIn;
  document.getElementById('user-info').hidden = !loggedIn;
  if (loggedIn) {
    document.getElementById('user-label').textContent = `#${state.userId} (${state.role})`;
    document.querySelector('.admin-only').hidden = state.role !== 'ADMIN';
    loadCourses();
  }
}

document.getElementById('signup-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  try {
    await apiFetch('/api/v1/auth/signup', {
      method: 'POST',
      auth: false,
      body: Object.fromEntries(form),
    });
    showToast('가입 완료! 이제 로그인하세요.');
    e.target.reset();
  } catch (err) {
    showToast(err.message, true);
  }
});

document.getElementById('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  try {
    const { accessToken } = await apiFetch('/api/v1/auth/login', {
      method: 'POST',
      auth: false,
      body: Object.fromEntries(form),
    });
    setSession(accessToken);
    showToast('로그인 성공');
    e.target.reset();
  } catch (err) {
    showToast(err.message, true);
  }
});

document.getElementById('logout-btn').addEventListener('click', () => {
  clearSession();
  showToast('로그아웃했습니다.');
});

// ---- 탭 ----

document.querySelectorAll('.tab-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach((p) => (p.hidden = true));
    btn.classList.add('active');
    const panel = document.getElementById(btn.dataset.tab);
    panel.hidden = false;
    if (btn.dataset.tab === 'courses-view') loadCourses();
    if (btn.dataset.tab === 'my-enrollments-view') loadMyEnrollments();
    if (btn.dataset.tab === 'admin-view') loadBotLogs();
  });
});

// ---- 과목 목록 / 수강신청 ----

async function loadCourses() {
  try {
    const { courses } = await apiFetch('/api/v1/courses');
    renderCourses(courses);
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderCourses(courses) {
  const body = document.getElementById('courses-body');
  body.innerHTML = '';
  for (const c of courses) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${c.courseCode}</td>
      <td>${c.name}</td>
      <td>${c.professorName}</td>
      <td>${c.semester}</td>
      <td>${c.capacity}</td>
      <td>${c.currentEnrolledCount}</td>
      <td>${c.remainingSeats}</td>
      <td>${c.queueLength}</td>
      <td>
        <button data-action="enroll" data-id="${c.id}">신청</button>
        <button class="secondary" data-action="queue-status" data-id="${c.id}">대기상태</button>
        <button class="secondary" data-action="queue-confirm" data-id="${c.id}">확정</button>
        <button class="secondary" data-action="queue-leave" data-id="${c.id}">포기</button>
      </td>`;
    body.appendChild(tr);
  }
}

document.getElementById('courses-body').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;
  const courseId = btn.dataset.id;
  try {
    switch (btn.dataset.action) {
      case 'enroll': {
        const res = await apiFetch(`/api/v1/courses/${courseId}/enroll`, { method: 'POST' });
        if (res.status === 'ENROLLED') {
          showToast('신청 완료(정원 내 즉시 확정)');
        } else {
          showToast(`대기열 진입: ${res.queuePosition}번째, 예상 대기 ${res.estimatedWaitSeconds}초`);
        }
        loadCourses();
        break;
      }
      case 'queue-status': {
        const res = await apiFetch(`/api/v1/courses/${courseId}/queue/status`);
        showToast(`대기상태: ${res.status}${res.position ? ` (${res.position}번째)` : ''}`);
        break;
      }
      case 'queue-confirm': {
        await apiFetch(`/api/v1/courses/${courseId}/queue/confirm`, { method: 'POST' });
        showToast('대기열 확정 완료 (신청 확정)');
        loadCourses();
        break;
      }
      case 'queue-leave': {
        await apiFetch(`/api/v1/courses/${courseId}/queue`, { method: 'DELETE' });
        showToast('대기열에서 나갔습니다.');
        loadCourses();
        break;
      }
    }
  } catch (err) {
    showToast(err.message, true);
  }
});

document.getElementById('refresh-courses').addEventListener('click', loadCourses);

// ---- 내 신청내역 ----

async function loadMyEnrollments() {
  try {
    const enrollments = await apiFetch('/api/v1/enrollments/me');
    renderEnrollments(enrollments);
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderEnrollments(enrollments) {
  const body = document.getElementById('enrollments-body');
  body.innerHTML = '';
  for (const en of enrollments) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${en.courseCode ?? '-'}</td>
      <td>${en.courseName ?? '-'}</td>
      <td>${en.status}</td>
      <td>${en.enrolledAt ?? '-'}</td>
      <td>${en.status === 'ENROLLED' ? `<button class="danger" data-id="${en.enrollmentId}">취소</button>` : ''}</td>`;
    body.appendChild(tr);
  }
}

document.getElementById('enrollments-body').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-id]');
  if (!btn) return;
  try {
    await apiFetch(`/api/v1/enrollments/${btn.dataset.id}`, { method: 'DELETE' });
    showToast('신청을 취소했습니다.');
    loadMyEnrollments();
  } catch (err) {
    showToast(err.message, true);
  }
});

document.getElementById('refresh-enrollments').addEventListener('click', loadMyEnrollments);

// ---- 관리자: 과목 등록 ----

document.getElementById('create-course-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  const payload = Object.fromEntries(form);
  payload.credit = Number(payload.credit);
  payload.capacity = Number(payload.capacity);
  payload.enrollmentStartAt = payload.enrollmentStartAt + ':00';
  payload.enrollmentEndAt = payload.enrollmentEndAt + ':00';
  try {
    const res = await apiFetch('/api/v1/admin/courses', { method: 'POST', body: payload });
    showToast(`과목 등록 완료 (id=${res.courseId})`);
    e.target.reset();
    loadCourses();
  } catch (err) {
    showToast(err.message, true);
  }
});

// ---- 관리자: 봇 탐지 로그 ----

async function loadBotLogs() {
  if (state.role !== 'ADMIN') return;
  try {
    const logs = await apiFetch('/api/v1/admin/bot-detection/logs');
    renderBotLogs(logs);
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderBotLogs(logs) {
  const body = document.getElementById('bot-logs-body');
  body.innerHTML = '';
  for (const log of logs) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${log.userId}</td>
      <td>${log.courseId}</td>
      <td class="features">${log.requestFeatures}</td>
      <td>${log.suspicionScore.toFixed(3)}</td>
      <td>${log.actionTaken}</td>
      <td>${log.createdAt}</td>`;
    body.appendChild(tr);
  }
}

document.getElementById('refresh-bot-logs').addEventListener('click', loadBotLogs);

// ---- 초기화 ----

if (state.token) {
  try {
    setSession(state.token);
  } catch {
    clearSession();
  }
}
