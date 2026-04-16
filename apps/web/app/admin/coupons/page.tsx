"use client"

import { useEffect, useRef, useState } from "react"
import { AlertCircle, Edit, Plus, Tag, ToggleLeft, ToggleRight, Trash2, X } from "lucide-react"
import { toast } from "sonner"
import { cn } from "@/lib/utils"
import { useLocale } from "@/lib/context"
import { adminCouponApi } from "@/services/api"
import { Modal } from "@/components/ui/modal"
import type { AdminCouponItem, CouponDiscountType } from "@/types"

type CouponForm = {
  name: string
  code: string
  discount_type: CouponDiscountType
  discount_value: string
  is_enabled: boolean
  remark: string
}

const EMPTY_FORM: CouponForm = {
  name: "",
  code: "",
  discount_type: "FIXED",
  discount_value: "",
  is_enabled: true,
  remark: "",
}

export default function AdminCouponsPage() {
  const { t } = useLocale()
  const [coupons, setCoupons] = useState<AdminCouponItem[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showModal, setShowModal] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<string | null>(null)
  const [editCoupon, setEditCoupon] = useState<AdminCouponItem | null>(null)
  const [formData, setFormData] = useState<CouponForm>(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState<Record<string, boolean>>({})
  const codeRef = useRef<HTMLInputElement>(null)

  const fetchCoupons = async () => {
    setLoading(true)
    try {
      const data = await adminCouponApi.getList()
      setCoupons(data)
    } catch {
      setCoupons([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCoupons()
  }, [])

  const resetForm = () => {
    setFormData(EMPTY_FORM)
    setFormErrors({})
    setEditCoupon(null)
  }

  const handleCloseModal = () => {
    setShowModal(false)
    resetForm()
  }

  const handleEdit = (coupon: AdminCouponItem) => {
    setEditCoupon(coupon)
    setFormData({
      name: coupon.name,
      code: coupon.code,
      discount_type: coupon.discount_type,
      discount_value: String(coupon.discount_value),
      is_enabled: coupon.is_enabled,
      remark: coupon.remark || "",
    })
    setFormErrors({})
    setShowModal(true)
  }

  const handleSave = async () => {
    const errors: Record<string, boolean> = {}
    if (!formData.name.trim()) errors.name = true
    if (!formData.code.trim()) errors.code = true
    if (!formData.discount_value.trim()) errors.discount_value = true
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      toast.error("请填写完整的优惠码信息")
      codeRef.current?.focus()
      return
    }

    setSaving(true)
    try {
      const payload = {
        name: formData.name.trim(),
        code: formData.code.trim().toUpperCase(),
        discount_type: formData.discount_type,
        discount_value: Number(formData.discount_value),
        is_enabled: formData.is_enabled,
        remark: formData.remark.trim() || undefined,
      }
      if (editCoupon) {
        await adminCouponApi.update(editCoupon.id, payload)
      } else {
        await adminCouponApi.create(payload)
      }
      toast.success("保存成功")
      handleCloseModal()
      await fetchCoupons()
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "保存失败")
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await adminCouponApi.delete(id)
      toast.success("删除成功")
      setShowDeleteConfirm(null)
      await fetchCoupons()
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "删除失败")
    }
  }

  const handleToggle = async (coupon: AdminCouponItem) => {
    try {
      await adminCouponApi.update(coupon.id, { is_enabled: !coupon.is_enabled })
      toast.success(coupon.is_enabled ? "已停用" : "已启用")
      await fetchCoupons()
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "操作失败")
    }
  }

  const renderDiscount = (coupon: AdminCouponItem) => {
    if (coupon.discount_type === "FIXED") {
      return `立减 ${Number(coupon.discount_value).toFixed(2)}`
    }
    return `${Number(coupon.discount_value).toFixed(0)}% 支付`
  }

  const renderStatus = (coupon: AdminCouponItem) => {
    if (!coupon.is_enabled) return { label: "已停用", className: "bg-slate-500/10 text-slate-600" }
    if (coupon.status === "USED") return { label: "已使用", className: "bg-emerald-500/10 text-emerald-600" }
    if (coupon.status === "LOCKED") return { label: "待支付占用中", className: "bg-amber-500/10 text-amber-600" }
    return { label: "可用", className: "bg-blue-500/10 text-blue-600" }
  }

  const isRuleLocked = editCoupon != null && editCoupon.status !== "AVAILABLE"

  if (loading) {
    return (
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{t("admin.coupons")}</h1>
          <p className="text-sm text-muted-foreground">{t("admin.couponsDesc")}</p>
        </div>
        <div className="flex items-center justify-center py-24">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{t("admin.coupons")}</h1>
          <p className="text-sm text-muted-foreground">{t("admin.couponsDesc")}</p>
        </div>
        <button
          type="button"
          className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
          onClick={() => {
            resetForm()
            setShowModal(true)
          }}
        >
          <Plus className="h-4 w-4" />
          新建优惠码
        </button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {coupons.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted-foreground">
            暂无优惠码
          </div>
        ) : coupons.map((coupon) => {
          const status = renderStatus(coupon)
          return (
            <div key={coupon.id} className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      <Tag className="h-5 w-5 text-primary" />
                    </div>
                    <div>
                      <h3 className="font-semibold text-foreground">{coupon.name}</h3>
                      <p className="font-mono text-sm uppercase text-primary">{coupon.code}</p>
                    </div>
                  </div>
                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <span className={cn("rounded-full px-2.5 py-1 text-xs font-medium", status.className)}>
                      {status.label}
                    </span>
                    <span className="rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-foreground">
                      {renderDiscount(coupon)}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                  onClick={() => handleToggle(coupon)}
                  title={coupon.is_enabled ? "停用" : "启用"}
                >
                  {coupon.is_enabled ? <ToggleRight className="h-5 w-5 text-primary" /> : <ToggleLeft className="h-5 w-5" />}
                </button>
              </div>

              <div className="mt-4 space-y-2 text-sm text-muted-foreground">
                <p>创建时间：{new Date(coupon.created_at).toLocaleString()}</p>
                {coupon.status === "LOCKED" && coupon.reserved_order_id && (
                  <p>占用订单：<span className="font-mono text-xs text-foreground">{coupon.reserved_order_id}</span></p>
                )}
                {coupon.status === "USED" && coupon.used_order_id && (
                  <p>使用订单：<span className="font-mono text-xs text-foreground">{coupon.used_order_id}</span></p>
                )}
                {coupon.remark && <p>备注：<span className="text-foreground">{coupon.remark}</span></p>}
              </div>

              <div className="mt-5 flex items-center justify-end gap-2">
                <button
                  type="button"
                  className="rounded-md p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                  onClick={() => handleEdit(coupon)}
                  title="编辑"
                >
                  <Edit className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  className="rounded-md p-2 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
                  onClick={() => setShowDeleteConfirm(coupon.id)}
                  title="删除"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          )
        })}
      </div>

      <Modal open={showModal} onClose={handleCloseModal} className="max-w-lg">
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 className="text-lg font-semibold text-foreground">{editCoupon ? "编辑优惠码" : "新建优惠码"}</h2>
          <button
            type="button"
            onClick={handleCloseModal}
            className="rounded-md p-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex flex-col gap-4 p-6">
          {isRuleLocked && (
            <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-700 dark:text-amber-300">
              已锁定或已使用的优惠码只建议修改名称、备注和启停状态，核心规则不能再改。
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-foreground">优惠码名称</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => {
                  setFormData(prev => ({ ...prev, name: e.target.value }))
                  setFormErrors(prev => ({ ...prev, name: false }))
                }}
                className={cn("h-10 rounded-lg border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2", formErrors.name ? "border-destructive ring-destructive/20" : "border-input focus:ring-ring")}
                placeholder="例如：新客九折"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-foreground">优惠码</label>
              <input
                ref={codeRef}
                type="text"
                value={formData.code}
                onChange={(e) => {
                  setFormData(prev => ({ ...prev, code: e.target.value.toUpperCase() }))
                  setFormErrors(prev => ({ ...prev, code: false }))
                }}
                className={cn("h-10 rounded-lg border bg-background px-3 text-sm text-foreground uppercase focus:outline-none focus:ring-2", formErrors.code ? "border-destructive ring-destructive/20" : "border-input focus:ring-ring")}
                placeholder="例如：WELCOME90"
                disabled={isRuleLocked}
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-foreground">优惠类型</label>
              <select
                value={formData.discount_type}
                onChange={(e) => setFormData(prev => ({ ...prev, discount_type: e.target.value as CouponDiscountType }))}
                className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                disabled={isRuleLocked}
              >
                <option value="FIXED">固定金额立减</option>
                <option value="PERCENT">折扣优惠</option>
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-foreground">
                {formData.discount_type === "FIXED" ? "优惠金额" : "折扣值"}
              </label>
              <input
                type="number"
                value={formData.discount_value}
                onChange={(e) => {
                  setFormData(prev => ({ ...prev, discount_value: e.target.value }))
                  setFormErrors(prev => ({ ...prev, discount_value: false }))
                }}
                className={cn("h-10 rounded-lg border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2", formErrors.discount_value ? "border-destructive ring-destructive/20" : "border-input focus:ring-ring")}
                placeholder={formData.discount_type === "FIXED" ? "例如：10" : "例如：90（表示 9 折）"}
                disabled={isRuleLocked}
              />
            </div>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-border bg-muted/20 px-4 py-3">
            <div>
              <p className="text-sm font-medium text-foreground">启用状态</p>
              <p className="text-xs text-muted-foreground">停用后用户将无法再使用该优惠码</p>
            </div>
            <button
              type="button"
              onClick={() => setFormData(prev => ({ ...prev, is_enabled: !prev.is_enabled }))}
              className="text-primary"
            >
              {formData.is_enabled ? <ToggleRight className="h-7 w-7" /> : <ToggleLeft className="h-7 w-7 text-muted-foreground" />}
            </button>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-foreground">备注</label>
            <textarea
              value={formData.remark}
              onChange={(e) => setFormData(prev => ({ ...prev, remark: e.target.value }))}
              className="min-h-24 rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              placeholder="可选，用于记录发放对象或使用说明"
            />
          </div>
        </div>
        <div className="flex justify-end gap-3 border-t border-border px-6 py-4">
          <button
            type="button"
            className="rounded-lg border border-input bg-transparent px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent"
            onClick={handleCloseModal}
          >
            {t("admin.cancel")}
          </button>
          <button
            type="button"
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? t("admin.saving") : t("admin.save")}
          </button>
        </div>
      </Modal>

      <Modal open={showDeleteConfirm !== null} onClose={() => setShowDeleteConfirm(null)} className="max-w-md">
        <div className="flex flex-col gap-4 p-6">
          <div className="flex items-start gap-3">
            <div className="rounded-full bg-destructive/10 p-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
            </div>
            <div className="flex-1">
              <h3 className="text-base font-semibold text-foreground">{t("admin.deleteConfirm")}</h3>
              <p className="mt-1 text-sm text-muted-foreground">删除后该优惠码将不再出现在后台列表中。</p>
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <button
              type="button"
              className="rounded-lg border border-input bg-transparent px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent"
              onClick={() => setShowDeleteConfirm(null)}
            >
              {t("admin.cancel")}
            </button>
            <button
              type="button"
              className="rounded-lg bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition-colors hover:bg-destructive/90"
              onClick={() => showDeleteConfirm && handleDelete(showDeleteConfirm)}
            >
              {t("admin.delete")}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
