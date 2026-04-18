"use client"

import { useEffect, useState } from "react"
import { ChevronLeft, ChevronRight, Eye, EyeOff, MessageSquare } from "lucide-react"
import { toast } from "sonner"
import { OrderStatusBadge } from "@/components/shared/order-status-badge"
import { useLocale } from "@/lib/context"
import { formatDateTime } from "@/lib/utils"
import { adminGuestbookApi } from "@/services/api"
import type { AdminGuestbookItem } from "@/types"

const PAGE_SIZE = 10

export default function AdminGuestbookPage() {
  const { t, locale } = useLocale()
  const [messages, setMessages] = useState<AdminGuestbookItem[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [pendingId, setPendingId] = useState<string | null>(null)

  useEffect(() => {
    const fetchMessages = async () => {
      setLoading(true)
      try {
        const data = await adminGuestbookApi.getList({ page, page_size: PAGE_SIZE })
        setMessages(data.list)
        setTotal(data.pagination.total)
      } catch (err) {
        setMessages([])
        setTotal(0)
        toast.error(err instanceof Error ? err.message : t("common.error"))
      } finally {
        setLoading(false)
      }
    }

    fetchMessages()
  }, [page, locale, t])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const handleToggleVisibility = async (item: AdminGuestbookItem) => {
    setPendingId(item.id)
    try {
      await adminGuestbookApi.updateVisibility(item.id, !item.is_visible)
      setMessages((prev) => prev.map((entry) => (
        entry.id === item.id ? { ...entry, is_visible: !entry.is_visible } : entry
      )))
      toast.success(item.is_visible ? t("admin.hidden") : t("admin.visible"))
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.error"))
    } finally {
      setPendingId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t("admin.guestbook")}</h1>
        <p className="text-sm text-muted-foreground">{t("admin.guestbookDesc")}</p>
      </div>

      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.user")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.orderNo")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.productSummary")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.messageContent")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.visibility")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.time")}</th>
                <th className="px-4 py-3 text-right font-medium text-muted-foreground">{t("admin.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-12">
                    <div className="flex items-center justify-center">
                      <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                    </div>
                  </td>
                </tr>
              ) : messages.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-10 text-center text-sm text-muted-foreground">
                    {t("admin.noGuestbookData")}
                  </td>
                </tr>
              ) : (
                messages.map((item) => (
                  <tr key={item.id} className="border-b border-border/50 align-top last:border-0">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{item.nickname || t("guestbook.defaultName")}</div>
                      {item.order_email && (
                        <div className="mt-1 text-xs text-muted-foreground">{item.order_email}</div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-mono text-xs text-foreground">{item.order_id}</div>
                      <div className="mt-2">
                        {item.order_status ? <OrderStatusBadge status={item.order_status} /> : "-"}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {item.product_summary || "-"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="max-w-xl whitespace-pre-wrap leading-6 text-foreground">{item.content}</div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={item.is_visible ? "text-emerald-600" : "text-muted-foreground"}>
                        {item.is_visible ? t("admin.visible") : t("admin.hidden")}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatDateTime(item.created_at, locale)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => handleToggleVisibility(item)}
                        disabled={pendingId === item.id}
                        className="inline-flex h-9 items-center gap-2 rounded-lg border border-input px-3 text-sm text-foreground transition-colors hover:bg-accent disabled:pointer-events-none disabled:opacity-50"
                      >
                        {item.is_visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                        {item.is_visible ? t("admin.hidden") : t("admin.visible")}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between border-t border-border px-4 py-3">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <MessageSquare className="h-4 w-4" />
            <span>{t("admin.totalRecords")} {total} {t("admin.records")}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setPage((prev) => prev - 1)}
              className="inline-flex h-9 items-center gap-1 rounded-lg border border-input px-3 text-sm text-foreground transition-colors hover:bg-accent disabled:pointer-events-none disabled:opacity-50"
            >
              <ChevronLeft className="h-4 w-4" />
              {t("admin.prevPage")}
            </button>
            <span className="text-sm text-muted-foreground">
              {page} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => setPage((prev) => prev + 1)}
              className="inline-flex h-9 items-center gap-1 rounded-lg border border-input px-3 text-sm text-foreground transition-colors hover:bg-accent disabled:pointer-events-none disabled:opacity-50"
            >
              {t("admin.nextPage")}
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
