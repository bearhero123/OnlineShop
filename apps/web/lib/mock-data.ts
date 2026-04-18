import type {
  ProductCard,
  ProductDetail,
  Category,
  CartItem,
  Cart,
  OrderBrief,
  OrderDetail,
  DeliverResult,
  DashboardStats,
  SalesTrend,
  CardKeyStockSummary,
  CardImportBatch,
  AdminUserItem,
  OperationLog,
  PaymentChannelItem,
  SiteConfig,
  RiskConfig,
  AdminOrderItem,
  PointRecord,
  PointsData,
  PaginatedData,
  UserProfile,
  CaptchaResult,
  AuthResult,
  OrderCardKey,
  SiteConfigKV,
  CreateOrderResult,
  PaymentCreateResult,
} from "@/types"

const uuid = (n: number) => `550e8400-e29b-41d4-a716-44665544${String(n).padStart(4, "0")}`

const nowIso = () => new Date().toISOString()

export const mockCategories: Category[] = []

export const mockProducts: ProductCard[] = []

const mockProductDetails: Record<string, ProductDetail> = {}

export const mockCartItems: CartItem[] = []

export const mockCart: Cart = {
  items: mockCartItems,
  total_amount: 0,
}

export const mockOrderBriefs: OrderBrief[] = []

export const mockOrderDetails: OrderDetail[] = []

export const mockDeliverResults: DeliverResult[] = []

export const mockDashboardStats: DashboardStats = {
  today_sales: 0,
  month_sales: 0,
  today_orders: 0,
  month_orders: 0,
  conversion_rate: 0,
  today_pv: 0,
  today_uv: 0,
  low_stock_products: [],
}

export const mockSalesTrend: SalesTrend[] = Array.from({ length: 14 }, (_, i) => {
  const date = new Date()
  date.setDate(date.getDate() - (13 - i))
  return {
    date: date.toISOString().split("T")[0],
    sales_amount: 0,
    order_count: 0,
  }
})

export const mockCardKeyStock: CardKeyStockSummary[] = []

export const mockImportBatches: CardImportBatch[] = []

export const mockOrderCardKeys: OrderCardKey[] = []

export const mockAdminUsers: AdminUserItem[] = [
  { id: uuid(701), username: "demo_user", email: "user@example.com", role: "USER", points: 0, is_deleted: 0, created_at: "2026-01-10T08:00:00Z" },
  { id: uuid(901), username: "admin", email: "admin@example.com", role: "ADMIN", points: 0, is_deleted: 0, created_at: "2026-01-01T00:00:00Z" },
]

export const mockOperationLogs: OperationLog[] = [
  {
    id: uuid(801),
    user_id: uuid(901),
    username: "admin",
    action: "site.config.update",
    target_type: "site_config",
    target_id: uuid(9901),
    detail: "Updated public site settings",
    ip_address: "127.0.0.1",
    created_at: "2026-02-01T14:00:00Z",
  },
]

export const mockPaymentChannels: PaymentChannelItem[] = []

export const mockSiteConfig: SiteConfig = {
  site_name: "OnlineShop",
  site_slogan: "Open-source storefront starter",
  site_description: "Public repository build with demo products, card keys, and private contacts removed.",
  announcement: "",
  announcement_enabled: false,
  popup_content: "",
  popup_enabled: false,
  contact_email: "",
  contact_telegram: "",
  contact_telegram_group: "",
  maintenance_enabled: false,
  points_enabled: false,
  points_rate: 1,
  footer_text: "OnlineShop",
  github_url: "https://github.com/bearhero123/OnlineShop",
}

export const mockSiteConfigKVs: SiteConfigKV[] = [
  { config_key: "site_name", config_value: "OnlineShop", config_group: "basic" },
  { config_key: "site_slogan", config_value: "Open-source storefront starter", config_group: "basic" },
  { config_key: "site_description", config_value: "Public repository build with demo products, card keys, and private contacts removed.", config_group: "basic" },
  { config_key: "announcement_enabled", config_value: "false", config_group: "announcement" },
  { config_key: "announcement", config_value: "", config_group: "announcement" },
  { config_key: "popup_enabled", config_value: "false", config_group: "popup" },
  { config_key: "popup_content", config_value: "", config_group: "popup" },
  { config_key: "contact_email", config_value: "", config_group: "contact" },
  { config_key: "contact_telegram", config_value: "", config_group: "contact" },
  { config_key: "contact_telegram_group", config_value: "", config_group: "contact" },
  { config_key: "maintenance_enabled", config_value: "false", config_group: "maintenance" },
  { config_key: "points_enabled", config_value: "false", config_group: "points" },
  { config_key: "points_rate", config_value: "1", config_group: "points" },
  { config_key: "footer_text", config_value: "OnlineShop", config_group: "basic" },
  { config_key: "github_url", config_value: "https://github.com/bearhero123/OnlineShop", config_group: "basic" },
]

export const mockRiskConfig: RiskConfig = {
  turnstile_enabled: false,
  device_rate_limit_enabled: false,
  device_order_limit_per_hour: 10,
  device_query_limit_per_hour: 20,
  device_login_limit_per_hour: 10,
  device_register_limit_per_hour: 5,
  rate_limit_per_second: 10,
  login_attempt_limit: 5,
  max_purchase_per_user: 100,
  max_pending_orders_per_ip: 5,
  max_pending_orders_per_user: 3,
  order_expire_minutes: 15,
}

export const mockPointRecords: PointRecord[] = []

export const mockAdminOrders: AdminOrderItem[] = []

export const mockUser: UserProfile = {
  id: uuid(701),
  username: "demo_user",
  email: "user@example.com",
  role: "USER",
  points: 0,
  created_at: "2026-01-10T08:00:00Z",
}

export const mockAdminUser: UserProfile = {
  id: uuid(901),
  username: "admin",
  email: "admin@example.com",
  role: "ADMIN",
  points: 0,
  created_at: "2026-01-01T00:00:00Z",
}

export function mockCaptcha(): CaptchaResult {
  return {
    captcha_id: uuid(9999),
    captcha_image: "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwIiBoZWlnaHQ9IjQwIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjx0ZXh0IHg9IjEwIiB5PSIzMCIgZm9udC1zaXplPSIyNCIgZmlsbD0iIzMzMyI+QUI4RjwvdGV4dD48L3N2Zz4=",
  }
}

export function mockLogin(): AuthResult {
  return { token: "mock-jwt-token-" + Date.now(), user: mockUser }
}

export function mockRegister(): AuthResult {
  return { token: "mock-jwt-token-" + Date.now(), user: mockUser }
}

export function mockProductList(params?: { category_id?: string; keyword?: string; page?: number; page_size?: number }): PaginatedData<ProductCard> {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: [],
    pagination: { page, page_size: pageSize, total: 0 },
  }
}

export function mockProductDetail(id: string): ProductDetail | null {
  return mockProductDetails[id] ?? null
}

export function mockCartData(): Cart {
  return { ...mockCart, items: [...mockCart.items] }
}

export function mockOrderList(params?: { status?: string; page?: number; page_size?: number }): PaginatedData<OrderBrief> {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: [],
    pagination: { page, page_size: pageSize, total: 0 },
  }
}

export function mockPointsData(params?: { page?: number; page_size?: number }): PointsData {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    total_points: 0,
    list: [],
    pagination: { page, page_size: pageSize, total: 0 },
  }
}

export function mockQueryOrders(_params: { order_ids?: string[]; emails?: string[] }): OrderBrief[] {
  return []
}

export function mockDeliver(orderIds: string[]): DeliverResult[] {
  return orderIds.map((id) => ({
    order_id: id,
    status: "PENDING",
    groups: [],
  }))
}

export function mockCreateOrder(email: string, _paymentMethod: string): CreateOrderResult {
  const orderId = uuid(Date.now() % 10000)
  const now = nowIso()
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString()

  const order: OrderDetail = {
    id: orderId,
    total_amount: 0,
    actual_amount: 0,
    status: "PENDING",
    order_type: "CART",
    payment_method: "unconfigured",
    created_at: now,
    email,
    points_deducted: 0,
    points_discount: 0,
    expires_at: expiresAt,
    paid_at: null,
    delivered_at: null,
    items: [],
  }

  const payment: PaymentCreateResult = {
    order_id: orderId,
    payment_url: "https://mock-payment.example.com/pay/" + orderId,
    expires_at: expiresAt,
  }

  return { order, payment }
}

export function mockAdminOrderList(params?: { status?: string; page?: number; page_size?: number }): PaginatedData<AdminOrderItem> {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: [],
    pagination: { page, page_size: pageSize, total: 0 },
  }
}

export function mockAdminUserList(params?: { keyword?: string; page?: number; page_size?: number }): PaginatedData<AdminUserItem> {
  let filtered = [...mockAdminUsers]
  if (params?.keyword) {
    const kw = params.keyword.toLowerCase()
    filtered = filtered.filter((u) => u.username.toLowerCase().includes(kw) || u.email.toLowerCase().includes(kw))
  }
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: filtered.slice((page - 1) * pageSize, page * pageSize),
    pagination: { page, page_size: pageSize, total: filtered.length },
  }
}

export function mockCardKeyStockList(_params?: { product_id?: string }): CardKeyStockSummary[] {
  return []
}

export function mockImportBatchList(params?: { page?: number; page_size?: number }): PaginatedData<CardImportBatch> {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: [],
    pagination: { page, page_size: pageSize, total: 0 },
  }
}

export function mockOperationLogList(params?: { page?: number; page_size?: number }): PaginatedData<OperationLog> {
  const page = params?.page ?? 1
  const pageSize = params?.page_size ?? 20
  return {
    list: mockOperationLogs.slice((page - 1) * pageSize, page * pageSize),
    pagination: { page, page_size: pageSize, total: mockOperationLogs.length },
  }
}
