/**
 * deviceSetupGuide.ts — datos de la guía enriquecida "Configuración de Dispositivos"
 * (el único artículo de Documentación con layout propio en vez del ArticleDetail
 * genérico). Todo el contenido de los pasos Android/iOS, requisitos, tarjetas de
 * gestión y tabla de contenidos vive aquí para que los componentes de UI
 * (SetupStepFlow, RequirementsAccordion, DeviceManagementCards, DocumentationToc)
 * sean puramente de presentación y reutilizables con otra configuración.
 */

import type { LucideIcon } from 'lucide-react'
import {
  Cable, Code2, Terminal, CheckCircle2, Lock,
  Smartphone, Star, Play,
} from 'lucide-react'

/** Color de los círculos numerados — igual para Android e iOS (mismo patrón visual). */
export const STEP_ACCENT = '#8b5cf6'

export interface SetupStepData {
  icon:        LucideIcon
  title:       string
  description: string
}

export interface OsSetupConfig {
  id:                 'android' | 'ios'
  sectionId:          string
  stepsId:            string
  requirementsId:     string
  heading:            string
  subtitle:           string
  steps:              SetupStepData[]
  requirementsTitle:  string
  requirements:       string[]
}

export const ANDROID_SETUP: OsSetupConfig = {
  id: 'android',
  sectionId: 'android',
  stepsId: 'android-pasos',
  requirementsId: 'android-requisitos',
  heading: '1. Conectar Dispositivo Android',
  subtitle: 'Sigue estos pasos para conectar un dispositivo Android al sistema.',
  steps: [
    { icon: Cable, title: 'Conecta el dispositivo', description: 'Conecta tu dispositivo Android al equipo mediante USB.' },
    { icon: Code2, title: 'Habilita Depuración USB', description: 'Activa la Depuración USB en las opciones de desarrollador.' },
    { icon: Terminal, title: 'Verifica la conexión', description: 'El dispositivo debe aparecer en la sección de Dispositivos Conectados.' },
    { icon: CheckCircle2, title: 'Configura el dispositivo', description: 'Selecciona el dispositivo y configúralo para ejecución.' },
  ],
  requirementsTitle: 'Requisitos Android',
  requirements: [
    'Dispositivo con Android 8.0 o superior',
    'Depuración USB habilitada en Opciones de Desarrollador',
    'Cable USB en buen estado (datos, no solo carga)',
    'Controladores ADB instalados en el equipo host',
  ],
}

export const IOS_SETUP: OsSetupConfig = {
  id: 'ios',
  sectionId: 'ios',
  stepsId: 'ios-pasos',
  requirementsId: 'ios-requisitos',
  heading: '2. Conectar Dispositivo iOS',
  subtitle: 'Sigue estos pasos para conectar un dispositivo iOS al sistema.',
  steps: [
    { icon: Cable, title: 'Conecta el dispositivo', description: 'Conecta tu iPhone o iPad al equipo mediante USB.' },
    { icon: Lock, title: 'Confía en el equipo', description: 'Acepta la confianza en el dispositivo cuando se solicite.' },
    { icon: Terminal, title: 'Verifica la conexión', description: 'El dispositivo debe aparecer en la sección de Dispositivos Conectados.' },
    { icon: CheckCircle2, title: 'Configura el dispositivo', description: 'Selecciona el dispositivo y configúralo para ejecución.' },
  ],
  requirementsTitle: 'Requisitos iOS',
  requirements: [
    'Dispositivo con iOS 13 o superior',
    'Cable Lightning o USB-C original',
    'Confianza del equipo aceptada en el dispositivo',
    'Perfil de desarrollo y WDA configurados en el Runner',
  ],
}

export interface ManagementCardData {
  icon:        LucideIcon
  color:       string
  title:       string
  description: string
  anchorId?:   string
}

export const MANAGEMENT_CARDS: ManagementCardData[] = [
  { icon: Smartphone, color: '#38bdf8', title: 'Seleccionar Dispositivo', description: 'Elige el dispositivo que deseas utilizar para ejecutar pruebas.' },
  { icon: CheckCircle2, color: '#10b981', title: 'Verificar Estado', description: 'Asegúrate de que el dispositivo esté disponible y en estado READY.', anchorId: 'gestion-estados' },
  { icon: Star, color: '#f59e0b', title: 'Establecer como Activo', description: 'Confirma el dispositivo como el dispositivo activo para ejecuciones.', anchorId: 'gestion-activo' },
  { icon: Play, color: '#8b5cf6', title: 'Ejecutar Pruebas', description: 'Ya puedes ejecutar casos, suites o pruebas en el dispositivo.' },
]

export interface TocItem {
  id:       string
  label:    string
  children?: { id: string; label: string }[]
}

export const GUIDE_TOC: TocItem[] = [
  { id: 'android', label: 'Conectar Dispositivo Android', children: [
    { id: 'android-requisitos', label: 'Requisitos Android' },
    { id: 'android-pasos', label: 'Pasos de Conexión' },
  ] },
  { id: 'ios', label: 'Conectar Dispositivo iOS', children: [
    { id: 'ios-requisitos', label: 'Requisitos iOS' },
    { id: 'ios-pasos', label: 'Pasos de Conexión' },
  ] },
  { id: 'gestion', label: 'Gestionar Dispositivos Configurados', children: [
    { id: 'gestion-estados', label: 'Estados de Dispositivo' },
    { id: 'gestion-activo', label: 'Dispositivo Activo' },
  ] },
  { id: 'problemas', label: 'Solución de Problemas', children: [
    { id: 'problemas-comunes', label: 'Problemas Comunes' },
    { id: 'problemas-verificaciones', label: 'Verificaciones' },
  ] },
  { id: 'proximos', label: 'Próximos Pasos' },
]

export const RELATED_ARTICLE_IDS = ['introduccion', 'instalacion-agent', 'ambientes-variables', 'ejecutar-casos']

export const GUIDE_META = {
  tiempoEstimado: '15 min',
  nivel: 'Principiantes',
  ultimaActualizacion: '20 agosto 2026',
}
