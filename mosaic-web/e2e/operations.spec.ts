import { expect, test, type Page } from '@playwright/test';

const project = { id: '11111111-1111-1111-1111-111111111111', name: '端到端验收项目', status: 'ACTIVE', description: 'P10', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' };
const layout = { id: '22222222-2222-2222-2222-222222222222', projectId: project.id, sourceAutoLayoutId: 'a', maskId: 'b', name: '已发布布局', state: 'PUBLISHED', canvasWidth: 1000, canvasHeight: 500, allowPhotoReuse: true, maxPhotoReuse: 3, currentRevision: 1, lockVersion: 1, currentVersionNumber: 2, currentVersionId: '33333333-3333-3333-3333-333333333333' };
const photo = { id: '44444444-4444-4444-4444-444444444444', originalFilename: 'duplicate.jpg', byteSize: 1024, status: 'DUPLICATE', analysisStatus: 'FAILED', reviewStatus: 'PENDING', duplicateOf: '55555555-5555-5555-5555-555555555555', failureMessage: '项目中已存在相同照片', createdAt: '2026-10-01T00:00:00Z' };

async function mockApi(page: Page, globalRole: 'ADMIN' | 'USER' = 'ADMIN') {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/api/v1/auth/login') return json({ tokenName: 'Authorization', tokenValue: 'e2e-token', tokenTimeout: 3600, activeTimeout: 1800, user: { id: 'u1', username: 'tester', displayName: '验收用户', globalRole } });
    if (path === '/api/v1/auth/me') return json({ id: 'u1', username: 'tester', displayName: '验收用户', globalRole });
    if (path === '/api/v1/projects') return json([project]);
    if (path.endsWith('/photos')) return json([photo]);
    if (path.endsWith('/jobs')) return json([]);
    if (path.endsWith('/print-renders') && request.method() === 'GET') return json([]);
    if (path.endsWith('/print-renders') && request.method() === 'POST') return json({ render: { id: 'r1', layoutId: layout.id, status: 'PENDING', format: 'TIFF', dpi: 300, outputByteSize: null, errorMessage: null, createdAt: '2026-10-01T00:00:00Z' }, jobId: '66666666-6666-6666-6666-666666666666', existing: false }, 201);
    if (path.endsWith('/editable-layouts')) return json([layout]);
    if (path.endsWith('/members')) return json([]);
    if (path === '/api/v1/admin/users') return json([]);
    if (path === '/api/v1/admin/audit-logs') return json([]);
    if (path === '/api/v1/layout/fonts') return json([{ id: 'noto', family: 'Noto Sans SC', version: '1', license: 'OFL', minimumWeight: 100, maximumWeight: 900 }]);
    if (path.endsWith('/text-masks')) return json([{ id: 'm1', name: '中国人能飞', text: '中国人能飞', direction: 'HORIZONTAL', canvasWidth: 2048, canvasHeight: 1024, slotCount: 100, status: 'READY', errorMessage: null, createdAt: '2026-10-01T00:00:00Z' }]);
    if (path.endsWith('/layouts')) return json([]);
    return json({ message: `未模拟接口：${request.method()} ${path}` }, 404);
  });
}

async function authenticate(page: Page, globalRole: 'ADMIN' | 'USER' = 'ADMIN') {
  await mockApi(page, globalRole);
  await page.addInitScript(() => localStorage.setItem('pics2text.token', 'e2e-token'));
}

test('账号登录后进入运营工作台', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), '桌面流程由桌面 Chrome 验收');
  await mockApi(page);
  await page.goto('/?view=login');
  await page.getByLabel('用户名').fill('tester');
  await page.getByLabel('密码').fill('tester');
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await expect(page.getByRole('heading', { name: '工作概览' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '端到端验收项目' })).toBeVisible();
});

test('运营人员可定位重复和异常照片', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), '桌面流程由桌面 Chrome 验收');
  await authenticate(page);
  await page.goto('/');
  await page.getByText('照片审核', { exact: true }).click();
  await page.getByText('重复照片', { exact: false }).click();
  await expect(page.getByText('duplicate.jpg')).toBeVisible();
  await expect(page.getByText('项目中已存在相同照片')).toBeVisible();
  await page.getByText('异常照片', { exact: true }).click();
  await expect(page.getByText('duplicate.jpg')).toBeVisible();
});

test('已发布布局可以创建印刷渲染任务', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), '桌面流程由桌面 Chrome 验收');
  await authenticate(page);
  await page.goto('/');
  await page.getByText('渲染交付', { exact: true }).click();
  await page.getByRole('button', { name: /创建印刷渲染/ }).click();
  await expect(page.getByRole('dialog', { name: '创建印刷渲染' })).toBeVisible();
  await page.getByRole('button', { name: '创建任务' }).click();
  await expect(page.getByText('PENDING')).toBeVisible();
});

test('普通用户看不到平台管理菜单', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), '桌面流程由桌面 Chrome 验收');
  await authenticate(page, 'USER');
  await page.goto('/');
  await expect(page.getByText('用户与角色', { exact: true })).toHaveCount(0);
  await expect(page.getByText('审计日志', { exact: true })).toHaveCount(0);
});

test('移动端通过抽屉导航访问任务中心', async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes('mobile'), '仅在移动端项目执行');
  await authenticate(page);
  await page.goto('/');
  await page.getByRole('button', { name: '打开导航' }).click();
  await page.getByLabel('Pics2Text').getByText('任务中心', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '任务中心' })).toBeVisible();
});
