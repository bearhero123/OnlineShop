"use client"

import { useEffect, useState, type FormEvent } from "react"
import { MessageSquare, Package, Send, ShieldCheck } from "lucide-react"
import { toast } from "sonner"
import { Textarea } from "@/components/ui/textarea"
import { useLocale } from "@/lib/context"
import { formatDateTime, stripInvisible } from "@/lib/utils"
import { getApiErrorMessage, guestbookApi } from "@/services/api"
import type { GuestbookMessageItem } from "@/types"

const PAGE_SIZE = 20

const EMPTY_FORM = {
  order_id: "",
  nickname: "",
  content: "",
}

export default function GuestbookPage() {
  const { t, locale } = useLocale()
  const [messages, setMessages] = useState<GuestbookMessageItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)

  useEffect(() => {
    const fetchMessages = async () => {
      setLoading(true)
      setLoadError(null)
      try {
        const data = await guestbookApi.getList({ page: 1, page_size: PAGE_SIZE })
        setMessages(data.list)
      } catch (err) {
        setMessages([])
        setLoadError(getApiErrorMessage(err, t))
      } finally {
        setLoading(false)
      }
    }

    fetchMessages()
  }, [locale, t])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const orderId = stripInvisible(form.order_id).trim()
    const nickname = stripInvisible(form.nickname).trim()
    const content = stripInvisible(form.content).trim()

    if (!orderId) {
      toast.error(t("guestbook.orderIdPlaceholder"))
      return
    }
    if (content.length < 5) {
      toast.error(t("guestbook.contentTooShort"))
      return
    }

    setSubmitting(true)
    try {
      const created = await guestbookApi.create({
        order_id: orderId,
        nickname: nickname || undefined,
        content,
      })
      setMessages((prev) => [created, ...prev].slice(0, PAGE_SIZE))
      setForm(EMPTY_FORM)
      toast.success(t("guestbook.submitSuccess"))
    } catch (err) {
      toast.error(getApiErrorMessage(err, t))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <section className="rounded-3xl border border-border bg-[radial-gradient(circle_at_top_left,rgba(14,165,233,0.14),transparent_36%),linear-gradient(135deg,rgba(255,255,255,0.96),rgba(248,250,252,0.9))] p-6 shadow-sm dark:bg-[radial-gradient(circle_at_top_left,rgba(56,189,248,0.16),transparent_34%),linear-gradient(135deg,rgba(15,23,42,0.94),rgba(17,24,39,0.92))]">
        <div className="flex flex-col gap-3">
          <div className="inline-flex w-fit items-center gap-2 rounded-full border border-sky-500/20 bg-sky-500/10 px-3 py-1 text-xs font-semibold text-sky-700 dark:text-sky-300">
            <MessageSquare className="h-3.5 w-3.5" />
            {t("guestbook.buyerOnly")}
          </div>
          <div>
            <h1 className="text-2xl font-bold text-foreground">{t("guestbook.title")}</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{t("guestbook.desc")}</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-2xl border border-border/60 bg-background/80 p-4">
              <div className="mb-2 inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <p className="text-sm font-medium text-foreground">{t("guestbook.orderRule")}</p>
            </div>
            <div className="rounded-2xl border border-border/60 bg-background/80 p-4">
              <div className="mb-2 inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                <MessageSquare className="h-4 w-4" />
              </div>
              <p className="text-sm font-medium text-foreground">{t("guestbook.publicHint")}</p>
            </div>
            <div className="rounded-2xl border border-border/60 bg-background/80 p-4">
              <div className="mb-2 inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Package className="h-4 w-4" />
              </div>
              <p className="text-sm font-medium text-foreground">{t("guestbook.formDesc")}</p>
            </div>
          </div>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-[360px,1fr]">
        <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-foreground">{t("guestbook.formTitle")}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t("guestbook.formDesc")}</p>
          </div>

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium text-foreground">{t("guestbook.orderId")}</label>
              <input
                type="text"
                value={form.order_id}
                onChange={(e) => setForm((prev) => ({ ...prev, order_id: e.target.value }))}
                placeholder={t("guestbook.orderIdPlaceholder")}
                className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-foreground">{t("guestbook.nickname")}</label>
              <input
                type="text"
                value={form.nickname}
                onChange={(e) => setForm((prev) => ({ ...prev, nickname: e.target.value }))}
                placeholder={t("guestbook.nicknamePlaceholder")}
                className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              />
              <p className="text-xs text-muted-foreground">{t("guestbook.nicknameHint")}</p>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-foreground">{t("guestbook.content")}</label>
              <Textarea
                value={form.content}
                onChange={(e) => setForm((prev) => ({ ...prev, content: e.target.value }))}
                placeholder={t("guestbook.contentPlaceholder")}
                className="min-h-[160px] resize-y"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
            >
              <Send className="h-4 w-4" />
              {submitting ? t("guestbook.submitting") : t("guestbook.submit")}
            </button>
          </form>
        </section>

        <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-foreground">{t("guestbook.recentTitle")}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t("guestbook.recentDesc")}</p>
          </div>

          {loading ? (
            <div className="flex min-h-[240px] items-center justify-center">
              <div className="h-7 w-7 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : loadError ? (
            <div className="rounded-xl border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
              {loadError}
            </div>
          ) : messages.length === 0 ? (
            <div className="rounded-xl border border-dashed border-border bg-muted/20 px-4 py-10 text-center text-sm text-muted-foreground">
              {t("guestbook.empty")}
            </div>
          ) : (
            <div className="space-y-4">
              {messages.map((message) => (
                <article key={message.id} className="rounded-2xl border border-border/70 bg-background/70 p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-foreground">
                      {message.nickname || t("guestbook.defaultName")}
                    </span>
                    <span className="rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground">
                      {t("guestbook.orderPrefix")} {message.masked_order_id}
                    </span>
                    {message.product_summary && (
                      <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs text-primary">
                        {t("guestbook.product")}: {message.product_summary}
                      </span>
                    )}
                  </div>
                  <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-foreground">{message.content}</p>
                  <p className="mt-3 text-xs text-muted-foreground">
                    {formatDateTime(message.created_at, locale)}
                  </p>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
