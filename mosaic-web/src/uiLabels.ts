const labels: Record<string, string> = {
  ACTIVE: '启用', ARCHIVED: '已归档', DISABLED: '已停用',
  ADMIN: '管理员', USER: '普通用户', PROJECT_ADMIN: '项目管理员', DESIGNER: '设计人员', REVIEWER: '审核人员', VIEWER: '只读成员',
  UPLOADING: '上传中', VERIFYING: '校验中', AVAILABLE: '可用', DUPLICATE: '重复', WITHDRAWN: '已撤回', DELETED: '已删除',
  PENDING: '等待处理', PROCESSING: '处理中', RUNNING: '运行中', RETRY_WAIT: '等待重试', SUCCEEDED: '已完成', FAILED: '失败',
  CANCELLED: '已取消', DEAD_LETTERED: '已转人工处理', APPROVED: '已通过', REJECTED: '已拒绝', READY: '已就绪', READY_FOR_APPROVAL: '待审批',
  DRAFT: '草稿', LOCKED: '已锁定', PUBLISHED: '已发布', VALID: '校验通过', INVALID: '校验不通过',
};

const jobTypes: Record<string, string> = {
  INFRASTRUCTURE_PROBE: '基础环境检查', PHOTO_ANALYSIS: '照片分析', TEXT_MASK_GENERATION: '文字蒙版生成',
  LAYOUT_GENERATION: '自动排版', LAYOUT_PREVIEW: '布局预览生成', PRINT_RENDER: '印刷成品渲染',
};

const auditActions: Record<string, string> = {
  LOGIN: '用户登录', LOGIN_SUCCESS: '登录成功', LOGIN_FAILED: '登录失败', LOGOUT: '退出登录', USER_REGISTER: '用户注册', USER_CREATE: '创建用户',
  USER_STATUS_CHANGE: '变更用户状态', USER_KICK: '强制用户下线', PROJECT_CREATE: '创建项目', PROJECT_ARCHIVE: '归档项目', PROJECT_ACTIVATE: '恢复项目',
  IDENTITY_BOOTSTRAP: '初始化身份数据', LOCAL_ADMIN_PROMOTE: '提升本地管理员', PROJECT_MEMBER_PUT: '配置项目成员', PROJECT_MEMBER_UPSERT: '配置项目成员', PROJECT_MEMBER_REMOVE: '移除项目成员', PHOTO_UPLOAD_INITIATE: '开始上传照片', PHOTO_UPLOAD_COMPLETE: '照片上传完成',
  PHOTO_DELETE: '删除照片', PHOTO_WITHDRAW: '撤回照片',
  TEXT_MASK_CREATE: '创建文字蒙版', AUTO_LAYOUT_CREATE: '创建自动排版', EDITABLE_LAYOUT_CREATE: '创建可编辑布局', LAYOUT_CREATE: '创建可编辑布局', LAYOUT_EDIT: '编辑布局', LAYOUT_AUTO_SAVE: '自动保存布局',
  LAYOUT_PUBLISH: '发布布局', LAYOUT_LOCK: '锁定布局', LAYOUT_UNLOCK: '解锁布局', LAYOUT_VERSION_RESTORE: '恢复布局版本', LAYOUT_PREVIEW_CREATE: '生成布局预览',
  SURFACE_TEMPLATE_CREATE: '创建曲面模板', SURFACE_LAYOUT_VALIDATE: '校验曲面布局', JOB_PROBE_CREATE: '创建环境检查任务', JOB_RETRY: '重试任务', JOB_CANCEL: '取消任务',
  RENDER_APPROVED: '印刷成品审批通过', RENDER_REJECTED: '印刷成品审批拒绝',
};

const targetTypes: Record<string, string> = {
  USER: '用户', PROJECT: '项目', PROJECT_MEMBER: '项目成员', PHOTO_ASSET: '照片', PROCESSING_JOB: '处理任务',
  TEXT_MASK: '文字蒙版', AUTO_LAYOUT: '自动排版', EDITABLE_LAYOUT: '可编辑布局', LAYOUT_PREVIEW: '布局预览', LAYOUT_VERSION: '布局版本', SURFACE_TEMPLATE: '曲面模板', PRINT_RENDER: '印刷成品',
};

export const statusLabel = (value: string) => labels[value] ?? '未知状态';
export const roleLabel = (value: string) => labels[value] ?? '未知角色';
export const jobTypeLabel = (value: string) => jobTypes[value] ?? '其他任务';
export const auditActionLabel = (value: string) => auditActions[value] ?? '其他操作';
export const targetTypeLabel = (value: string) => targetTypes[value] ?? '其他对象';
