import React, { createContext, useContext, useEffect, useState } from 'react'

type Theme = 'light' | 'dark' | 'system'

interface ThemeContextValue {
  theme: Theme
  resolvedTheme: 'light' | 'dark'
  setTheme: (t: Theme) => void
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

const STORAGE_KEY = 'devticket-theme'

function getSystemTheme(): 'light' | 'dark' {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(resolved: 'light' | 'dark') {
  const root = document.documentElement
  root.setAttribute('data-theme', resolved)

  // CSS 변수 오버라이드
  if (resolved === 'dark') {
    root.style.setProperty('--bg',        '#0F172A')
    root.style.setProperty('--surface',   '#1E293B')
    root.style.setProperty('--surface-2', '#263047')
    root.style.setProperty('--surface-3', '#334155')
    root.style.setProperty('--text',      '#F1F5F9')
    root.style.setProperty('--text-2',    '#CBD5E1')
    root.style.setProperty('--text-3',    '#94A3B8')
    root.style.setProperty('--text-4',    '#64748B')
    root.style.setProperty('--border',    '#1E293B')
    root.style.setProperty('--border-2',  '#334155')
    root.style.setProperty('--brand-light', 'rgba(79,70,229,0.2)')
  } else {
    root.style.removeProperty('--bg')
    root.style.removeProperty('--surface')
    root.style.removeProperty('--surface-2')
    root.style.removeProperty('--surface-3')
    root.style.removeProperty('--text')
    root.style.removeProperty('--text-2')
    root.style.removeProperty('--text-3')
    root.style.removeProperty('--text-4')
    root.style.removeProperty('--border')
    root.style.removeProperty('--border-2')
    root.style.removeProperty('--brand-light')
  }
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    return (localStorage.getItem(STORAGE_KEY) as Theme) ?? 'system'
  })

  const resolvedTheme: 'light' | 'dark' =
    theme === 'system' ? getSystemTheme() : theme

  useEffect(() => {
    applyTheme(resolvedTheme)
  }, [resolvedTheme])

  // system theme 변경 감지
  useEffect(() => {
    if (theme !== 'system') return
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = () => applyTheme(mq.matches ? 'dark' : 'light')
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [theme])

  const setTheme = (t: Theme) => {
    setThemeState(t)
    localStorage.setItem(STORAGE_KEY, t)
  }

  const toggleTheme = () => {
    setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')
  }

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme, setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be inside ThemeProvider')
  return ctx
}
