import type { TestSuite, Country } from './types';

export const COUNTRIES: Country[] = [
  { id: 'mexico',    name: 'México',    flag: '🇲🇽', hasSubMenu: false },
  { id: 'argentina', name: 'Argentina', flag: '🇦🇷', hasSubMenu: true  },
  { id: 'chile',     name: 'Chile',     flag: '🇨🇱', hasSubMenu: false },
  { id: 'colombia',  name: 'Colombia',  flag: '🇨🇴', hasSubMenu: false },
  { id: 'peru',      name: 'Perú',      flag: '🇵🇪', hasSubMenu: false },
  { id: 'espana',    name: 'España',    flag: '🇪🇸', hasSubMenu: false },
];

export const TEST_SUITES: TestSuite[] = [
  {
    id: 'flujo-completo',
    title: 'Flujo Completo',
    description: 'Ejecuta el flujo completo de compra de boletos, alimentos y checkout.',
    icon: '🎬',
    accent: '#3b82f6',
  },
  {
    id: 'asientos',
    title: 'Asientos',
    description: 'Valida selección de asientos, disponibilidad y cambios de horario.',
    icon: '💺',
    accent: '#9333ea',
  },
  {
    id: 'alimentos',
    title: 'Alimentos — Todo',
    description: 'Ejecuta todos los menús: Atmosphera, Coffee Tree, Mi Cine, Tradicional y VIP.',
    icon: '🍿',
    accent: '#f97316',
  },
  {
    id: 'carrito',
    title: 'Carrito de Compras',
    description: 'Valida productos en el carrito, códigos promocionales y resumen de compra.',
    icon: '🛒',
    accent: '#22c55e',
  },
  {
    id: 'checkout',
    title: 'Checkout',
    description: 'Valida el proceso de pago, métodos disponibles y confirmación de compra.',
    icon: '💳',
    accent: '#14b8a6',
  },
  {
    id: 'smoke',
    title: 'Smoke Tests',
    description: 'Suite rápida para validar funcionalidades críticas de la aplicación.',
    icon: '🐛',
    accent: '#eab308',
  },
];

export const ALIMENTOS_TESTS: TestSuite[] = [
  {
    id: 'alimentos-atmosfera',
    title: 'Menú Atmosphera',
    description: 'Valida combos y productos del menú Atmosphera.',
    icon: '✨',
    accent: '#f97316',
  },
  {
    id: 'alimentos-coffee',
    title: 'Menú Coffee Tree',
    description: 'Valida combos y productos del menú Coffee Tree.',
    icon: '☕',
    accent: '#a16207',
  },
  {
    id: 'alimentos-micine',
    title: 'Menú Mi Cine',
    description: 'Valida combos y productos del menú Mi Cine.',
    icon: '🎭',
    accent: '#dc2626',
  },
  {
    id: 'alimentos-tradicional',
    title: 'Menú Tradicional',
    description: 'Valida combos y productos del menú Tradicional.',
    icon: '🌮',
    accent: '#d97706',
  },
  {
    id: 'alimentos-vip',
    title: 'Menú VIP',
    description: 'Valida combos y productos del menú VIP.',
    icon: '⭐',
    accent: '#7c3aed',
  },
];

export const ENVIRONMENTS = ['QA', 'PROD', 'STG'];
export const SUITES       = ['Smoke Tests', 'Full Suite', 'Regresión', 'Sanity'];
export const DEVICES      = ['Galaxy A56 5G', 'Galaxy S23', 'Pixel 7', 'BrowserStack'];
