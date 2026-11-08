import React from 'react'

interface PageHeaderProps {
  title: string
  subtitle?: React.ReactNode
  actions?: React.ReactNode
}

export const PageHeader: React.FC<PageHeaderProps> = ({ title, subtitle, actions }) => (
  <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 mb-6">
    <div>
      <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900">{title}</h1>
      {subtitle && <p className="text-sm text-slate-500 mt-1">{subtitle}</p>}
    </div>
    {actions && <div className="flex items-center gap-2">{actions}</div>}
  </div>
)

export const PageContainer: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">{children}</div>
)

export const EmptyState: React.FC<{ title: string; children?: React.ReactNode }> = ({ title, children }) => (
  <div className="rounded-2xl border border-dashed border-slate-300 bg-white/60 p-10 text-center">
    <p className="font-semibold text-slate-700">{title}</p>
    {children && <div className="text-sm text-slate-500 mt-1">{children}</div>}
  </div>
)
