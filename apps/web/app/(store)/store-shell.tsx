"use client"

import React from "react"
import { usePathname } from "next/navigation"
import { Wrench, Mail } from "lucide-react"
import { StoreHeader } from "@/components/layout/store-header"
import { StoreFooter } from "@/components/layout/store-footer"
import { VisitTracker } from "@/components/store/visit-tracker"
import { useSiteConfig, useAuth, useLocale } from "@/lib/context"

function MaintenancePage() {
  const { t } = useLocale()
  const { config } = useSiteConfig()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4">
      <div className="flex flex-col items-center text-center">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-muted">
          <Wrench className="h-10 w-10 text-muted-foreground" />
        </div>
        <h1 className="mb-3 text-2xl font-bold text-foreground">{t("maintenance.title")}</h1>
        <p className="mb-6 max-w-md text-sm text-muted-foreground">
          {config?.maintenance_message || t("maintenance.description")}
        </p>
        {config?.contact_email && (
          <a
            href={`mailto:${config.contact_email}`}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
          >
            <Mail className="h-4 w-4" />
            {t("maintenance.contactSupport")}
          </a>
        )}
      </div>
    </div>
  )
}

function MaintenanceAdminBanner() {
  const { t } = useLocale()

  return (
    <div className="bg-amber-500/10 text-amber-700 dark:text-amber-400">
      <div className="mx-auto max-w-7xl px-4 py-1.5 text-center text-xs font-medium">
        {t("maintenance.adminBanner")}
      </div>
    </div>
  )
}

/** Routes that are always accessible even in maintenance mode */
const MAINTENANCE_EXEMPT_PATHS = ["/login", "/register"]

interface StoreShellProps {
  siteName: string
  children: React.ReactNode
}

export function StoreShell({ siteName, children }: StoreShellProps) {
  const { config, isLoading } = useSiteConfig()
  const { user, isLoggedIn, authLoaded } = useAuth()
  const pathname = usePathname()

  // 维护模式判断需要 config + auth 都就绪才能准确决策
  const configReady = !isLoading && !!config
  const isExempt = MAINTENANCE_EXEMPT_PATHS.includes(pathname)
  const isAdmin = authLoaded && isLoggedIn && user?.role === "ADMIN"
  const isMaintenance = configReady && config?.maintenance_enabled

  // Maintenance mode: block non-admin users on non-exempt pages
  // auth 未就绪时暂不渲染内容，避免管理员被误拦（auth 读 localStorage，通常 <1 帧）
  if (isMaintenance && !isExempt) {
    if (!authLoaded) return null
    if (!isAdmin) return <MaintenancePage />
  }

  return (
    <div className="flex min-h-screen flex-col">
      <VisitTracker />
      {isMaintenance && isAdmin && <MaintenanceAdminBanner />}
      <StoreHeader siteName={siteName} />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 lg:px-6">{children}</main>
      <StoreFooter />
    </div>
  )
}
