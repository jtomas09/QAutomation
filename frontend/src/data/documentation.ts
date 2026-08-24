/**
 * documentation.ts — estructura de datos de la sección "Documentación".
 *
 * DocumentationCategory agrupa DocumentationArticle[] — cada artículo tiene
 * el contenido mínimo para render + búsqueda (título/descripción/categoría/
 * tags). El `content` de cada artículo es PLACEHOLDER a propósito (ver nota
 * en cada uno) — esta sección estructura la navegación y el diseño, no
 * documentación técnica real todavía inexistente en el proyecto.
 */

import type { LucideIcon } from 'lucide-react'
import {
  Rocket, Smartphone, PlayCircle, BarChart3,
  BookOpen, Download, ListChecks,
  Settings2, Globe2, Plug, SlidersHorizontal,
  Layers3, CalendarClock, GitBranch,
  LineChart, FileOutput, History,
  Video, Code2, HelpCircle, Headphones,
} from 'lucide-react'

export type DocCategoryId = 'primeros-pasos' | 'configuracion' | 'ejecucion' | 'reportes'

export interface DocumentationArticle {
  id:          string
  title:       string
  description: string
  category:    DocCategoryId
  icon:        LucideIcon
  /** Identificador de navegación — hoy solo se usa internamente (ArticleDetail),
   *  preparado para volverse una ruta real si la app adopta un router. */
  route:       string
  tags:        string[]
  /** Placeholder — párrafos genéricos hasta que exista contenido técnico real. */
  content:     string[]
}

export interface DocumentationCategory {
  id:    DocCategoryId
  title: string
  icon:  LucideIcon
  color: string
}

export const DOC_CATEGORIES: DocumentationCategory[] = [
  { id: 'primeros-pasos', title: 'Primeros Pasos', icon: Rocket,    color: '#8b5cf6' },
  { id: 'configuracion',  title: 'Configuración',   icon: Settings2, color: '#10b981' },
  { id: 'ejecucion',      title: 'Ejecución',       icon: PlayCircle, color: '#f59e0b' },
  { id: 'reportes',       title: 'Reportes',        icon: BarChart3, color: '#38bdf8' },
]

const PLACEHOLDER_BODY = (title: string): string[] => [
  `Este artículo ("${title}") todavía no tiene contenido técnico redactado — este texto es un placeholder estructurado, listo para reemplazarse.`,
  'Cuando se complete, aquí se explicará paso a paso el flujo correspondiente, con ejemplos y capturas relevantes de Automation QA.',
]

export const DOC_ARTICLES: DocumentationArticle[] = [
  // ── Primeros Pasos ──────────────────────────────────────────────────────
  {
    id: 'introduccion', title: 'Introducción a Automation QA',
    description: 'Qué es Automation QA y cómo se organiza la plataforma.',
    category: 'primeros-pasos', icon: BookOpen, route: 'introduccion',
    tags: ['introducción', 'overview', 'plataforma'],
    content: PLACEHOLDER_BODY('Introducción a Automation QA'),
  },
  {
    id: 'instalacion-agent', title: 'Instalación del Agent',
    description: 'Descarga e instala el Runner Agent en tu máquina.',
    category: 'primeros-pasos', icon: Download, route: 'instalacion-agent',
    tags: ['agent', 'runner', 'instalación', 'setup'],
    content: PLACEHOLDER_BODY('Instalación del Agent'),
  },
  {
    id: 'requisitos-sistema', title: 'Requisitos del Sistema',
    description: 'Sistemas operativos, dependencias y puertos necesarios.',
    category: 'primeros-pasos', icon: ListChecks, route: 'requisitos-sistema',
    tags: ['requisitos', 'sistema', 'dependencias'],
    content: PLACEHOLDER_BODY('Requisitos del Sistema'),
  },
  {
    id: 'primeros-pasos-guia', title: 'Primeros Pasos',
    description: 'Comienza a ejecutar tus primeras pruebas en minutos.',
    category: 'primeros-pasos', icon: Rocket, route: 'primeros-pasos-guia',
    tags: ['inicio rápido', 'primeros pasos', 'quickstart'],
    content: PLACEHOLDER_BODY('Primeros Pasos'),
  },

  // ── Configuración ───────────────────────────────────────────────────────
  {
    id: 'config-dispositivos', title: 'Configuración de Dispositivos',
    description: 'Aprende a conectar y configurar dispositivos Android e iOS.',
    category: 'configuracion', icon: Smartphone, route: 'config-dispositivos',
    tags: ['dispositivos', 'android', 'ios', 'udid'],
    content: PLACEHOLDER_BODY('Configuración de Dispositivos'),
  },
  {
    id: 'ambientes-variables', title: 'Ambientes y Variables',
    description: 'Configura ambientes (QA/Staging/Producción) y variables por país.',
    category: 'configuracion', icon: Globe2, route: 'ambientes-variables',
    tags: ['ambientes', 'variables', 'entornos', 'país'],
    content: PLACEHOLDER_BODY('Ambientes y Variables'),
  },
  {
    id: 'integraciones', title: 'Integraciones',
    description: 'Conecta Automation QA con otras herramientas de tu equipo.',
    category: 'configuracion', icon: Plug, route: 'integraciones',
    tags: ['integraciones', 'notificaciones', 'email'],
    content: PLACEHOLDER_BODY('Integraciones'),
  },
  {
    id: 'config-avanzada', title: 'Configuración Avanzada',
    description: 'Ajustes avanzados del Runner, del Backend y del Dashboard.',
    category: 'configuracion', icon: SlidersHorizontal, route: 'config-avanzada',
    tags: ['avanzado', 'configuración', 'runner', 'backend'],
    content: PLACEHOLDER_BODY('Configuración Avanzada'),
  },

  // ── Ejecución ────────────────────────────────────────────────────────────
  {
    id: 'ejecutar-casos', title: 'Ejecutar Casos de Prueba',
    description: 'Ejecuta casos individuales grabados desde Record Studio.',
    category: 'ejecucion', icon: PlayCircle, route: 'ejecutar-casos',
    tags: ['casos', 'record studio', 'ejecutar'],
    content: PLACEHOLDER_BODY('Ejecutar Casos de Prueba'),
  },
  {
    id: 'ejecutar-suites', title: 'Ejecutar Suites',
    description: 'Ejecuta casos, suites y pruebas en múltiples dispositivos.',
    category: 'ejecucion', icon: Layers3, route: 'ejecutar-suites',
    tags: ['suites', 'multi-dispositivo', 'ejecutar'],
    content: PLACEHOLDER_BODY('Ejecutar Suites'),
  },
  {
    id: 'ejecucion-programada', title: 'Ejecución Programada',
    description: 'Programa ejecuciones automáticas con expresiones cron.',
    category: 'ejecucion', icon: CalendarClock, route: 'ejecucion-programada',
    tags: ['programación', 'cron', 'automático'],
    content: PLACEHOLDER_BODY('Ejecución Programada'),
  },
  {
    id: 'paralelizacion', title: 'Paralelización',
    description: 'Corre pruebas en paralelo en varios dispositivos a la vez.',
    category: 'ejecucion', icon: GitBranch, route: 'paralelizacion',
    tags: ['paralelización', 'paralelo', 'device farm'],
    content: PLACEHOLDER_BODY('Paralelización'),
  },

  // ── Reportes ─────────────────────────────────────────────────────────────
  {
    id: 'entender-reportes', title: 'Entender los Reportes',
    description: 'Cómo leer los resultados PASSED/FAILED/SKIPPED de una ejecución.',
    category: 'reportes', icon: BarChart3, route: 'entender-reportes',
    tags: ['reportes', 'resultados', 'passed', 'failed'],
    content: PLACEHOLDER_BODY('Entender los Reportes'),
  },
  {
    id: 'metricas-analisis', title: 'Métricas y Análisis',
    description: 'Analiza tendencias, tasas de éxito y tiempos promedio.',
    category: 'reportes', icon: LineChart, route: 'metricas-analisis',
    tags: ['métricas', 'análisis', 'tendencias'],
    content: PLACEHOLDER_BODY('Métricas y Análisis'),
  },
  {
    id: 'exportar-resultados', title: 'Exportar Resultados',
    description: 'Exporta reportes en PDF y comparte resultados por correo.',
    category: 'reportes', icon: FileOutput, route: 'exportar-resultados',
    tags: ['exportar', 'pdf', 'correo'],
    content: PLACEHOLDER_BODY('Exportar Resultados'),
  },
  {
    id: 'historial-ejecuciones', title: 'Historial de Ejecuciones',
    description: 'Consulta y filtra el historial completo de ejecuciones.',
    category: 'reportes', icon: History, route: 'historial-ejecuciones',
    tags: ['historial', 'ejecuciones', 'búsqueda'],
    content: PLACEHOLDER_BODY('Historial de Ejecuciones'),
  },

  // ── Recursos adicionales (sin categoría de las 4 principales) ────────────
  {
    id: 'videos-tutoriales', title: 'Videos Tutoriales',
    description: 'Tutoriales paso a paso en video.',
    category: 'primeros-pasos', icon: Video, route: 'videos-tutoriales',
    tags: ['videos', 'tutoriales'],
    content: PLACEHOLDER_BODY('Videos Tutoriales'),
  },
  {
    id: 'api-reference', title: 'API Reference',
    description: 'Documentación de API completa.',
    category: 'configuracion', icon: Code2, route: 'api-reference',
    tags: ['api', 'referencia', 'endpoints'],
    content: PLACEHOLDER_BODY('API Reference'),
  },
  {
    id: 'faq', title: 'Preguntas Frecuentes',
    description: 'Respuestas a dudas comunes.',
    category: 'primeros-pasos', icon: HelpCircle, route: 'faq',
    tags: ['faq', 'preguntas', 'dudas'],
    content: PLACEHOLDER_BODY('Preguntas Frecuentes'),
  },
  {
    id: 'soporte-tecnico', title: 'Soporte Técnico',
    description: 'Obtén ayuda del equipo.',
    category: 'primeros-pasos', icon: Headphones, route: 'soporte-tecnico',
    tags: ['soporte', 'ayuda', 'contacto'],
    content: PLACEHOLDER_BODY('Soporte Técnico'),
  },
]

export function getArticleById(id: string): DocumentationArticle | undefined {
  return DOC_ARTICLES.find(a => a.id === id)
}

export function getArticlesByCategory(category: DocCategoryId): DocumentationArticle[] {
  return DOC_ARTICLES.filter(a => a.category === category)
}

/** Búsqueda simple sobre título/descripción/categoría/tags — sin dependencias externas. */
export function searchArticles(query: string): DocumentationArticle[] {
  const q = query.trim().toLowerCase()
  if (!q) return []
  return DOC_ARTICLES.filter(a =>
    a.title.toLowerCase().includes(q)
    || a.description.toLowerCase().includes(q)
    || a.category.toLowerCase().includes(q)
    || a.tags.some(t => t.toLowerCase().includes(q)),
  )
}
