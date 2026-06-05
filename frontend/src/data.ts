import type { TestSuite, Country, IndividualTest } from './types';

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
    title: 'Alimentos',
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

export const ARGENTINA_SUITES: TestSuite[] = [
  {
    id: 'noafectacion-argentina',
    title: 'No Afectación Argentina',
    description: 'Suite completa de pruebas de compra para todos los cines de Argentina sin sesión.',
    icon: '🎬',
    accent: '#6366f1',
  },
];

export const CHILE_SUITES: TestSuite[] = [
  {
    id: 'noafectacion-chile',
    title: 'No Afectación Chile',
    description: 'Suite completa de pruebas de compra para todos los cines de Chile sin sesión.',
    icon: '🎬',
    accent: '#ef4444',
  },
];

// Maps country id → suite cards shown on Execute page
export const COUNTRY_SUITES: Record<string, TestSuite[]> = {
  mexico:    TEST_SUITES,
  argentina: ARGENTINA_SUITES,
  chile:     CHILE_SUITES,
};

export const ENVIRONMENTS = ['QA', 'PROD', 'STG'];
export const SUITES       = ['Smoke Tests', 'Full Suite', 'Regresión', 'Sanity'];
export const DEVICES      = ['Galaxy A56 5G', 'Galaxy S23', 'Pixel 7', 'BrowserStack'];

// Individual tests shown when user drills into a suite card
export const SUITE_TESTS: Record<string, IndividualTest[]> = {

  'asientos': [
    { id: 'asientos-seleccion1',    title: 'Selección de 1 Asiento',        description: 'Selecciona un asiento disponible y continúa' },
    { id: 'asientos-multiples',     title: 'Múltiples Asientos',             description: 'Selecciona 3 asientos disponibles y continúa' },
    { id: 'asientos-consecutivos',  title: 'Asientos Consecutivos',          description: 'Selecciona 3 asientos consecutivos' },
    { id: 'asientos-deseleccion',   title: 'Selección y Deselección',        description: 'Selecciona y deselecciona 3 asientos' },
    { id: 'asientos-10mas',         title: 'Más de 10 Asientos',             description: 'Selecciona más de 10 y valida alerta' },
    { id: 'asientos-horario',       title: 'Cambio de Horario',              description: 'Cambia el horario en el mapa de asientos' },
    { id: 'asientos-3d',            title: 'Asientos 3D',                    description: 'Verifica el banner en sala 3D' },
    { id: 'asientos-especial',      title: 'Alerta Asiento Especial',        description: 'Valida alerta en asiento especial' },
    { id: 'asientos-junior',        title: 'Sala Junior',                    description: 'Verifica el banner en sala Junior' },
  ],

  'flujo-completo': [
    { id: 'e2e-ticket-trad',    title: 'Compra Ticket — Tradicional',    description: 'Compra ticket sin sesión en sala Tradicional' },
    { id: 'e2e-mix-trad',       title: 'Compra Mix — Tradicional',       description: 'Compra ticket + alimento sin sesión, Tradicional' },
    { id: 'e2e-alimento-trad',  title: 'Compra Alimento — Tradicional',  description: 'Solo alimento sin sesión en sala Tradicional' },
    { id: 'e2e-ticket-atmos',   title: 'Compra Ticket — Atmósfera',      description: 'Compra ticket sin sesión en sala Atmósfera' },
    { id: 'e2e-mix-atmos',      title: 'Compra Mix — Atmósfera',         description: 'Compra ticket + alimento sin sesión, Atmósfera' },
    { id: 'e2e-alimento-atmos', title: 'Compra Alimento — Atmósfera',    description: 'Solo alimento sin sesión en sala Atmósfera' },
    { id: 'e2e-ticket-vip',     title: 'Compra Ticket — VIP',            description: 'Compra ticket sin sesión en sala VIP' },
    { id: 'e2e-mix-vip',        title: 'Compra Mix — VIP',               description: 'Compra ticket + alimento sin sesión, VIP' },
    { id: 'e2e-alimento-vip',   title: 'Compra Alimento — VIP',          description: 'Solo alimento sin sesión en sala VIP' },
    { id: 'pase-anual',         title: 'Compra con Pase Anual',          description: 'Aplica folio de Pase Anual Cinépolis en la pantalla de boletos' },
  ],

  'checkout': [
    { id: 'e2e-ticket-trad',    title: 'Compra Ticket — Tradicional',    description: 'Flujo completo de checkout en sala Tradicional' },
    { id: 'e2e-mix-trad',       title: 'Compra Mix — Tradicional',       description: 'Checkout ticket + alimento en sala Tradicional' },
    { id: 'e2e-alimento-trad',  title: 'Compra Alimento — Tradicional',  description: 'Checkout solo alimento en sala Tradicional' },
    { id: 'e2e-ticket-atmos',   title: 'Compra Ticket — Atmósfera',      description: 'Flujo completo de checkout en sala Atmósfera' },
    { id: 'e2e-mix-atmos',      title: 'Compra Mix — Atmósfera',         description: 'Checkout ticket + alimento en sala Atmósfera' },
    { id: 'e2e-alimento-atmos', title: 'Compra Alimento — Atmósfera',    description: 'Checkout solo alimento en sala Atmósfera' },
    { id: 'e2e-ticket-vip',     title: 'Compra Ticket — VIP',            description: 'Flujo completo de checkout en sala VIP' },
    { id: 'e2e-mix-vip',        title: 'Compra Mix — VIP',               description: 'Checkout ticket + alimento en sala VIP' },
    { id: 'e2e-alimento-vip',   title: 'Compra Alimento — VIP',          description: 'Checkout solo alimento en sala VIP' },
  ],

  // "Alimentos — Todo" drills to the 5 menus
  'alimentos': [
    { id: 'alimentos-atmosfera',    title: 'Menú Atmosphera',    description: 'Valida combos y productos del menú Atmosphera' },
    { id: 'alimentos-coffee',       title: 'Menú Coffee Tree',   description: 'Valida combos y productos del menú Coffee Tree' },
    { id: 'alimentos-micine',       title: 'Menú Mi Cine',       description: 'Valida combos y productos del menú Mi Cine' },
    { id: 'alimentos-tradicional',  title: 'Menú Tradicional',   description: 'Valida combos y productos del menú Tradicional' },
    { id: 'alimentos-vip',          title: 'Menú VIP',           description: 'Valida combos y productos del menú VIP' },
  ],

  'alimentos-atmosfera': [
    { id: 'atmos-t1', title: 'Crepa Dulce y Frappé de Frutos Rojos',    description: 'Combo Crepa Dulce y Frappé de Frutos Rojos' },
    { id: 'atmos-t2', title: 'Frappé de Coco y Crepa Dulce con Queso',  description: 'Combo Frappé de Coco y Crepa Dulce con Queso' },
    { id: 'atmos-t3', title: 'Frappé Sandía Pelonada y Crepa de Fresa', description: 'Combo Frappé Sandía Pelonada y Crepa de Fresa' },
  ],

  'alimentos-vip': [
    { id: 'vip-t1', title: 'Palomitas Clásicas de Mantequilla', description: 'Comprar Palomitas Clásicas de Mantequilla' },
    { id: 'vip-t2', title: 'Dippin Dots de Algodón de Azúcar',  description: 'Comprar Dippin Dots de Algodón de Azúcar' },
  ],

  'alimentos-coffee': [
    { id: 'coffee-t01', title: 'Americano Grande con Coco',                   description: 'Comprar Americano Grande con Coco' },
    { id: 'coffee-t02', title: 'Americano Descafeinado con Crema Irlandesa',  description: 'Comprar Americano Descafeinado con Crema Irlandesa' },
    { id: 'coffee-t03', title: 'Americano Mediano con Menta',                 description: 'Comprar Americano Mediano con Menta' },
    { id: 'coffee-t04', title: 'Americano Mediano con Vainilla',              description: 'Comprar Americano Mediano con Vainilla' },
    { id: 'coffee-t05', title: 'Moka Oscuro con Coco',                        description: 'Comprar Moka Oscuro con Coco' },
    { id: 'coffee-t06', title: 'Moka Oscuro Descafeinado con Leche Deslact.', description: 'Comprar Moka Oscuro Descafeinado con Leche Deslactosada' },
    { id: 'coffee-t07', title: 'Moka Oscuro Mediano con Leche de Almendra',   description: 'Comprar Moka Oscuro Mediano con Leche de Almendra' },
    { id: 'coffee-t08', title: 'Moka Oscuro Mediano con Vainilla',            description: 'Comprar Moka Oscuro Mediano con Vainilla' },
    { id: 'coffee-t09', title: 'Capuccino con Coco',                          description: 'Comprar Capuccino con Coco' },
    { id: 'coffee-t10', title: 'Té Caliente de Jamaica con Coco',             description: 'Comprar Té Caliente de Jamaica con Coco' },
    { id: 'coffee-t11', title: 'Té Caliente Mediano con Menta',               description: 'Comprar Té Caliente Mediano con Menta' },
    { id: 'coffee-t12', title: 'Chocolate con Coco',                          description: 'Comprar Chocolate con Coco' },
    { id: 'coffee-t13', title: 'Chocolate Mediano con Crema Irlandesa',       description: 'Comprar Chocolate Mediano con Crema Irlandesa' },
    { id: 'coffee-t14', title: 'Pretzel',                                     description: 'Comprar Pretzel' },
    { id: 'coffee-t15', title: 'Cheese Cake',                                 description: 'Comprar Cheese Cake' },
    { id: 'coffee-t16', title: 'Cornetto',                                    description: 'Comprar Cornetto' },
    { id: 'coffee-t17', title: 'Skwinkles Chunks',                            description: 'Comprar Skwinkles Chunks' },
    { id: 'coffee-t18', title: "M&M's",                                       description: "Comprar M&M's" },
    { id: 'coffee-t19', title: "Hershey's",                                   description: "Comprar Hershey's" },
    { id: 'coffee-t20', title: 'Snickers',                                    description: 'Comprar Snickers' },
    { id: 'coffee-t21', title: 'Crepa Dulce Premium',                         description: 'Comprar Crepa Dulce Premium' },
    { id: 'coffee-t22', title: 'Crepa de Manzana Canela',                     description: 'Comprar Crepa de Manzana Canela' },
    { id: 'coffee-t23', title: 'Crepa Salada Premium Hawaiana',               description: 'Comprar Crepa Salada Premium Hawaiana' },
    { id: 'coffee-t24', title: 'Crepa Salada de Champiqueso',                 description: 'Comprar Crepa Salada de Champiqueso' },
    { id: 'coffee-t25', title: 'Crepa Salada Italiana con Queso Manchego',    description: 'Comprar Crepa Salada Italiana con Queso Manchego' },
    { id: 'coffee-t26', title: 'Combo Crepa Salada con Queso y Frappé',       description: 'Combo Crepa Salada con Queso y Frappé con Coco' },
    { id: 'coffee-t27', title: 'Combo Crepa Salada de Pavo y Frappé Sandía',  description: 'Combo Crepa Salada de Pavo y Frappé de Sandía' },
    { id: 'coffee-t28', title: 'Combo Crepa con Tocino y Frappé Crema',       description: 'Combo Crepa con Tocino y Frappé con Crema Irlandesa' },
    { id: 'coffee-t29', title: 'Combo Crepa con Champiñón y Frappé Mango',    description: 'Combo Crepa con Champiñón y Frappé de Mango Tajín' },
    { id: 'coffee-t30', title: 'Combo Crepa con extras y Frappé Vainilla',    description: 'Combo Crepa con extras y Frappé con Vainilla' },
  ],

  'alimentos-micine': [
    { id: 'micine-t01', title: 'Maxi Combo Mix — Caramelo',           description: 'Comprar Maxi Combo Mix con Palomitas de Caramelo' },
    { id: 'micine-t02', title: 'Maxi Combo Mix — Doritos',            description: 'Comprar Maxi Combo Mix con Palomitas de Doritos' },
    { id: 'micine-t03', title: 'Maxi Combo Mix — Takis',              description: 'Comprar Maxi Combo Mix con Palomitas Takis' },
    { id: 'micine-t04', title: 'Maxi Combo Mix — Cheetos Mix',        description: 'Comprar Maxi Combo Mix con Palomitas Cheetos Mix' },
    { id: 'micine-t05', title: 'Maxi Combo Familiar — Refrescos Light', description: 'Comprar Maxi Combo Familiar con Refrescos Light' },
    { id: 'micine-t06', title: 'Maxi Combo Familiar — Refrescos Zero', description: 'Comprar Maxi Combo Familiar con Refrescos Zero' },
    { id: 'micine-t07', title: 'Maxi Combo Familiar — Sprite',        description: 'Comprar Maxi Combo Familiar con Sprite' },
    { id: 'micine-t08', title: 'Maxi Combo Familiar Jumbo — M&Ms',    description: 'Comprar Maxi Combo Familiar Jumbo con M&Ms' },
    { id: 'micine-t09', title: 'Maxi Combo Familiar Jumbo — Cheetos', description: 'Comprar Maxi Combo Familiar Jumbo con Cheetos y M&Ms' },
    { id: 'micine-t10', title: 'Maxi Combo Familiar Jumbo — Takis',   description: 'Comprar Maxi Combo Familiar Jumbo con Takis y M&Ms' },
    { id: 'micine-t11', title: 'Maxi Combo Familiar Jumbo — Doritos', description: 'Comprar Maxi Combo Familiar Jumbo con Doritos y M&Ms' },
    { id: 'micine-t12', title: 'Maxi Combo Familiar Jumbo — Sprite',  description: 'Comprar Maxi Combo Familiar Jumbo con Sprite' },
    { id: 'micine-t13', title: 'Maxi Combo Familiar Jumbo — Cheetos/Fanta', description: 'Comprar Maxi Combo Familiar Jumbo con Cheetos y Fanta' },
    { id: 'micine-t14', title: 'Combo ICEE — Skwinkles',              description: 'Comprar Combo ICEE con Skwinkles y Topping' },
    { id: 'micine-t15', title: 'Combo ICEE — Frambuesa Azul',         description: 'Comprar Combo ICEE con Frambuesa Azul y Topping' },
    { id: 'micine-t16', title: 'Combo ICEE — Mango',                  description: 'Comprar Combo ICEE con Mango y Topping' },
    { id: 'micine-t17', title: 'Combo ICEE — Mango + Frambuesa',      description: 'Comprar Combo ICEE con Mango, Frambuesa y Topping' },
    { id: 'micine-t18', title: 'Combo ICEE — Skwinkles Rellenos',     description: 'Comprar Combo ICEE con Topping y Skwinkles Rellenos' },
    { id: 'micine-t19', title: 'Combo ICEE — Skwinkles Salsaguetti',  description: 'Comprar Combo ICEE con Topping y Skwinkles Salsaguetti' },
    { id: 'micine-t20', title: "Combo ICEE — M&M's Cacahuate",        description: "Comprar Combo ICEE con Topping y M&M's Cacahuate" },
    { id: 'micine-t21', title: "Combo ICEE — M&M's Chocolate",        description: "Comprar Combo ICEE con Topping y M&M's Chocolate" },
    { id: 'micine-t22', title: 'Combo Junior — Coca Cola Mediano',    description: 'Combo Junior con Refrescos Mediano Coca Cola y Skittles' },
    { id: 'micine-t23', title: 'Combo Junior — Sprite sin Azúcar',    description: 'Combo Junior con Palomitas Mantequilla y Sprite sin Azúcar' },
    { id: 'micine-t24', title: 'Combo Junior — Sidral / Coca Light',  description: 'Combo Junior con Palomitas Caramelo y Sidral Mundet' },
    { id: 'micine-t25', title: 'Combo Junior — Fanta Naranja',        description: 'Combo Junior con Palomitas Doritos Nachos y Fanta Naranja' },
    { id: 'micine-t26', title: 'Combo Junior — Coca Light + Sprite',  description: 'Combo Junior con Palomitas Takis y Coca Cola Light' },
    { id: 'micine-t27', title: 'Combo Clásico — Hot Dog Chico',       description: 'Combo Clásico con Palomitas Mantequilla y Hot Dog Chico' },
    { id: 'micine-t28', title: 'Combo Clásico — Poco Hielo',          description: 'Combo Clásico con Palomitas Mantequilla, Poco Hielo' },
    { id: 'micine-t29', title: 'Combo Clásico — Sin Hielo',           description: 'Combo Clásico con Palomitas Mantequilla, Sin Hielo' },
    { id: 'micine-t30', title: 'Combo Clásico — Palomitas Caramelo',  description: 'Combo Clásico con Palomitas Caramelo y Hot Dog Chico' },
    { id: 'micine-t31', title: 'Combo Clásico — Palomitas Takis',     description: 'Combo Clásico con Palomitas Takis Fuego y Hot Dog Chico' },
    { id: 'micine-t32', title: 'Combo Clásico — Palomitas Doritos',   description: 'Combo Clásico con Palomitas Doritos Nachos y Hot Dog Chico' },
    { id: 'micine-t33', title: 'Combo Clásico — Sprite sin Azúcar',   description: 'Combo Clásico con Palomitas Mantequilla y Sprite sin Azúcar' },
    { id: 'micine-t34', title: 'Combo Clásico — Sidral Mundet',       description: 'Combo Clásico con Palomitas Mantequilla y Sidral Mundet' },
    { id: 'micine-t35', title: 'Combo Clásico — Hot Dog Jumbo',       description: 'Combo Clásico con Palomitas Mantequilla y Hot Dog Jumbo' },
    { id: 'micine-t36', title: 'Palomitas Skwinkles — Para Llevar',   description: 'Palomitas Skwinkles Para Llevar Mantequilla' },
    { id: 'micine-t37', title: 'Palomitas Skwinkles — Jumbo',         description: 'Palomitas Skwinkles Jumbo Mantequilla' },
    { id: 'micine-t38', title: 'Palomitas Skwinkles — Grandes',       description: 'Palomitas Skwinkles Grandes Mantequilla' },
    { id: 'micine-t39', title: 'Palomitas Skwinkles — Medianas',      description: 'Palomitas Skwinkles Medianas Mantequilla' },
    { id: 'micine-t40', title: 'Palomitas Skwinkles — Chicas',        description: 'Palomitas Skwinkles Chicas Mantequilla' },
  ],

  'noafectacion-argentina': [
    { id: 'ar-ticket-avellaneda',    title: 'Ticket — Avellaneda',       description: 'Compra ticket sin sesión en Cine Avellaneda' },
    { id: 'ar-mix-avellaneda',       title: 'Mix — Avellaneda',          description: 'Compra mix sin sesión en Cine Avellaneda' },
    { id: 'ar-food-avellaneda',      title: 'Alimento — Avellaneda',     description: 'Compra alimento sin sesión en Cine Avellaneda' },
    { id: 'ar-ticket-lujan',         title: 'Ticket — Lujan',            description: 'Compra ticket sin sesión en Cine Lujan' },
    { id: 'ar-mix-lujan',            title: 'Mix — Lujan',               description: 'Compra mix sin sesión en Cine Lujan' },
    { id: 'ar-food-lujan',           title: 'Alimento — Lujan',          description: 'Compra alimento sin sesión en Cine Lujan' },
    { id: 'ar-ticket-merlo',         title: 'Ticket — Merlo',            description: 'Compra ticket sin sesión en Cine Merlo' },
    { id: 'ar-mix-merlo',            title: 'Mix — Merlo',               description: 'Compra mix sin sesión en Cine Merlo' },
    { id: 'ar-food-merlo',           title: 'Alimento — Merlo',          description: 'Compra alimento sin sesión en Cine Merlo' },
    { id: 'ar-ticket-pilar',         title: 'Ticket — Pilar',            description: 'Compra ticket sin sesión en Cine Pilar' },
    { id: 'ar-mix-pilar',            title: 'Mix — Pilar',               description: 'Compra mix sin sesión en Cine Pilar' },
    { id: 'ar-food-pilar',           title: 'Alimento — Pilar',          description: 'Compra alimento sin sesión en Cine Pilar' },
    { id: 'ar-ticket-houssay',       title: 'Ticket — Plaza Houssay',    description: 'Compra ticket sin sesión en Cine Plaza Houssay' },
    { id: 'ar-mix-houssay',          title: 'Mix — Plaza Houssay',       description: 'Compra mix sin sesión en Cine Plaza Houssay' },
    { id: 'ar-food-houssay',         title: 'Alimento — Plaza Houssay',  description: 'Compra alimento sin sesión en Cine Plaza Houssay' },
    { id: 'ar-ticket-recoleta',      title: 'Ticket — Recoleta',         description: 'Compra ticket sin sesión en Cine Recoleta' },
    { id: 'ar-mix-recoleta',         title: 'Mix — Recoleta',            description: 'Compra mix sin sesión en Cine Recoleta' },
    { id: 'ar-food-recoleta',        title: 'Alimento — Recoleta',       description: 'Compra alimento sin sesión en Cine Recoleta' },
    { id: 'ar-ticket-arenamaipu',    title: 'Ticket — Arena Maipu',      description: 'Compra ticket sin sesión en Cine Arena Maipu' },
    { id: 'ar-mix-arenamaipu',       title: 'Mix — Arena Maipu',         description: 'Compra mix sin sesión en Cine Arena Maipu' },
    { id: 'ar-food-arenamaipu',      title: 'Alimento — Arena Maipu',    description: 'Compra alimento sin sesión en Cine Arena Maipu' },
    { id: 'ar-ticket-mendozaplaza',  title: 'Ticket — Mendoza Plaza',    description: 'Compra ticket sin sesión en Cine Mendoza Plaza' },
    { id: 'ar-mix-mendozaplaza',     title: 'Mix — Mendoza Plaza',       description: 'Compra mix sin sesión en Cine Mendoza Plaza' },
    { id: 'ar-food-mendozaplaza',    title: 'Alimento — Mendoza Plaza',  description: 'Compra alimento sin sesión en Cine Mendoza Plaza' },
    { id: 'ar-ticket-neuquen',       title: 'Ticket — Neuquen',          description: 'Compra ticket sin sesión en Cine Neuquen' },
    { id: 'ar-mix-neuquen',          title: 'Mix — Neuquen',             description: 'Compra mix sin sesión en Cine Neuquen' },
    { id: 'ar-food-neuquen',         title: 'Alimento — Neuquen',        description: 'Compra alimento sin sesión en Cine Neuquen' },
    { id: 'ar-ticket-rosario',       title: 'Ticket — Rosario',          description: 'Compra ticket sin sesión en Cine Rosario' },
    { id: 'ar-mix-rosario',          title: 'Mix — Rosario',             description: 'Compra mix sin sesión en Cine Rosario' },
    { id: 'ar-food-rosario',         title: 'Alimento — Rosario',        description: 'Compra alimento sin sesión en Cine Rosario' },
  ],

  'noafectacion-chile': [
    { id: 'cl-ticket-dominicos',       title: 'Ticket — Los Dominicos',        description: 'Compra ticket sin sesión en Cine Los Dominicos' },
    { id: 'cl-mix-dominicos',          title: 'Mix — Los Dominicos',           description: 'Compra mix sin sesión en Cine Los Dominicos' },
    { id: 'cl-alimento-dominicos',     title: 'Alimento — Los Dominicos',      description: 'Compra alimento sin sesión en Cine Los Dominicos' },
    { id: 'cl-ticket-lareina',         title: 'Ticket — La Reina',             description: 'Compra ticket sin sesión en Cine La Reina' },
    { id: 'cl-mix-lareina',            title: 'Mix — La Reina',                description: 'Compra mix sin sesión en Cine La Reina' },
    { id: 'cl-alimento-lareina',       title: 'Alimento — La Reina',           description: 'Compra alimento sin sesión en Cine La Reina' },
    { id: 'cl-ticket-parque',          title: 'Ticket — Parque Arauco',        description: 'Compra ticket sin sesión en Cine Parque Arauco' },
    { id: 'cl-mix-parque',             title: 'Mix — Parque Arauco',           description: 'Compra mix sin sesión en Cine Parque Arauco' },
    { id: 'cl-alimento-parque',        title: 'Alimento — Parque Arauco',      description: 'Compra alimento sin sesión en Cine Parque Arauco' },
    { id: 'cl-ticket-parquepremium',   title: 'Ticket — Parque Arauco Premium',description: 'Compra ticket sin sesión en Cine Parque Arauco Premium' },
    { id: 'cl-mix-parquepremium',      title: 'Mix — Parque Arauco Premium',   description: 'Compra mix sin sesión en Cine Parque Arauco Premium' },
    { id: 'cl-alimento-parquepremium', title: 'Alimento — Parque Arauco Premium', description: 'Compra alimento sin sesión en Cine Parque Arauco Premium' },
  ],

  'pase-anual': [
    {
      id:          'compra-pase-anual',
      title:       'Compra con Pase Anual — Aplicar Folio',
      description: 'Selecciona función y asiento, luego aplica un folio de Pase Anual en la pantalla de boletos.',
    },
  ],

  // smoke entry required so SUITE_TESTS['smoke'] is truthy (drill-down guard in App.tsx)
  'smoke': [],

  'alimentos-tradicional': [
    { id: 'trad-t01', title: 'Maxi Combo Familiar — Takis + Refrescos',    description: 'Maxi Combo Familiar con Palomitas Takis y Refrescos' },
    { id: 'trad-t02', title: 'Maxi Combo Familiar — Refrescos variados',   description: 'Maxi Combo Familiar con Refrescos variados' },
    { id: 'trad-t03', title: 'Maxi Combo Familiar — Palomitas Caramelo',   description: 'Maxi Combo Familiar con Palomitas Caramelo' },
    { id: 'trad-t04', title: 'Maxi Combo Familiar — Takis (Variante)',     description: 'Maxi Combo Familiar con Palomitas Takis (Variante)' },
    { id: 'trad-t05', title: 'Maxi Combo Familiar — Palomitas Doritos',    description: 'Maxi Combo Familiar con Palomitas Doritos' },
    { id: 'trad-t06', title: 'Maxi Combo Familiar — Refrescos sin Hielo',  description: 'Maxi Combo Familiar con Refrescos sin Hielo' },
    { id: 'trad-t07', title: 'Maxi Combo Familiar — Palomitas Mixtas',     description: 'Maxi Combo Familiar con Palomitas Mixtas' },
    { id: 'trad-t08', title: 'Maxi Combo Familiar — Takis + Refrescos Mixtos', description: 'Maxi Combo Familiar con Palomitas Takis y Refrescos Mixtos' },
    { id: 'trad-t09', title: 'Combo ICEE — Topping Sirena',                description: 'Combo ICEE con Topping Sirena' },
    { id: 'trad-t10', title: 'Combo ICEE — Skittles',                      description: 'Combo ICEE con Skittles' },
    { id: 'trad-t11', title: 'Combo ICEE — Takis + Skwinkles Rellenos',    description: 'Combo ICEE con Palomitas Takis y Skwinkles Rellenos' },
    { id: 'trad-t12', title: 'Combo ICEE — Caramelo + Salsagheti',         description: 'Combo ICEE con Palomitas Caramelo y Skwinkles Salsagheti' },
    { id: 'trad-t13', title: 'Combo ICEE — Doritos + Pelon',               description: 'Combo ICEE con Palomitas Doritos y Pelon Pelonazo' },
    { id: 'trad-t14', title: 'Combo ICEE Jumbo — Aritos Enchilados',       description: 'Combo ICEE Jumbo con Aritos Enchilados' },
    { id: 'trad-t15', title: 'Combo ICEE Jumbo — doble Cereza + Skittles', description: 'Combo ICEE Jumbo con doble Cereza y Skittles' },
    { id: 'trad-t16', title: 'Combo ICEE Jumbo — doble Mango + Skwinkles', description: 'Combo ICEE Jumbo con doble Mango y Skwinkles Rellenos' },
    { id: 'trad-t17', title: 'Combo ICEE Jumbo — Caramelo + Salsagheti',   description: 'Combo ICEE Jumbo con Palomitas Caramelo y Salsagheti' },
    { id: 'trad-t18', title: 'Combo ICEE Jumbo — Doritos + Pelonazo',      description: 'Combo ICEE Jumbo con Palomitas Doritos y Pelonazo' },
    { id: 'trad-t19', title: 'Combo ICEE — Salsagheti',                    description: 'Combo ICEE con Salsagheti' },
    { id: 'trad-t20', title: 'Combo ICEE — Takis + Pelonazo',              description: 'Combo ICEE con Palomitas Takis y Pelonazo' },
    { id: 'trad-t21', title: 'Combo ICEE Jumbo — Caramelo + Aritos',       description: 'Combo ICEE Jumbo con Palomitas Caramelo y Aritos' },
    { id: 'trad-t22', title: 'Combo ICEE Jumbo — Takis + Skittles',        description: 'Combo ICEE Jumbo con Palomitas Takis y Skittles' },
    { id: 'trad-t23', title: 'Hot Dog — Takis + Refresco',                 description: 'Combo Hot Dog Takis y Refresco' },
    { id: 'trad-t24', title: 'Hot Dog — Takis + Sidral Grande',            description: 'Combo Hot Dog Takis y Sidral Grande' },
    { id: 'trad-t25', title: 'Hot Dog — Takis + Sprite Mediano',           description: 'Combo Hot Dog Takis y Sprite Mediano' },
    { id: 'trad-t26', title: 'Hot Dog — Takis + Coca Light Chica',         description: 'Combo Hot Dog Takis y Coca-Cola Light Chica' },
    { id: 'trad-t27', title: 'Hot Dog Jumbo — Jumbo + Refresco Jumbo',     description: 'Combo Hot Dog Takis Jumbo y Refresco Jumbo' },
    { id: 'trad-t28', title: 'Hot Dog Jumbo — Jumbo + Refresco Grande',    description: 'Combo Hot Dog Takis Jumbo y Refresco Grande' },
    { id: 'trad-t29', title: 'Hot Dog Jumbo — Jumbo + Refresco Mediano',   description: 'Combo Hot Dog Takis Jumbo y Refresco Mediano' },
    { id: 'trad-t30', title: 'Hot Dog Jumbo — Jumbo + Refresco Chico',     description: 'Combo Hot Dog Takis Jumbo y Refresco Chico' },
    { id: 'trad-t31', title: 'Hot Dog — Takis Chico + Refresco Jumbo',     description: 'Combo Hot Dog Takis Chico y Refresco Jumbo' },
    { id: 'trad-t32', title: 'Hot Dog — Takis Jumbo + Sidral Jumbo',       description: 'Combo Hot Dog Takis Jumbo y Sidral Jumbo' },
    { id: 'trad-t33', title: 'Snacks — Papas + Agua + Nachos Clásicos',    description: 'Combo Papas, Agua y Nachos Clásicos' },
    { id: 'trad-t34', title: 'Snacks — Papas + Agua + Nachos Doritos',     description: 'Combo Papas, Agua y Nachos Doritos' },
    { id: 'trad-t35', title: 'Snacks — Papas Adobadas + Agua + Nachos',    description: 'Combo Papas Adobadas, Agua y Nachos' },
    { id: 'trad-t36', title: 'Snacks — Papas Adobadas + Nachos Doritos',   description: 'Combo Papas Adobadas, Agua y Nachos Doritos' },
    { id: 'trad-t37', title: 'Snacks — Papas Adobadas + Nachos Tajín',     description: 'Combo Papas Adobadas, Agua y Nachos Tajín' },
    { id: 'trad-t38', title: 'Snacks — Papas Adobadas 600ml + Tajín',      description: 'Combo Papas Adobadas, Agua 600ml y Nachos Tajín' },
    { id: 'trad-t39', title: 'Snacks — Papas Naturales + Nachos Takis',    description: 'Combo Papas Naturales, Agua 600ml y Nachos Takis' },
    { id: 'trad-t40', title: 'Snacks — Papas Naturales 1L + Nachos Takis', description: 'Combo Papas Naturales, Agua 1L y Nachos Chicos Takis' },
    { id: 'trad-t41', title: 'Snacks — Papas Adobadas 1L + Nachos Doritos',description: 'Combo Papas Adobadas, Agua 1L y Nachos Chicos Doritos' },
    { id: 'trad-t42', title: 'Maxicombo Mix — Nachos Clásicos',            description: 'Comprar Maxicombo Mix con Nachos Clásicos' },
    { id: 'trad-t43', title: 'Maxicombo Mix — Caramelo + Nachos Doritos',  description: 'Maxicombo Mix con Palomitas Caramelo y Nachos Doritos' },
    { id: 'trad-t44', title: 'Maxicombo Mix — Takis + Nachos Doritos',     description: 'Maxicombo Mix con Palomitas Takis y Nachos Doritos sin queso' },
    { id: 'trad-t45', title: 'Maxicombo Mix Jumbo — Hot Dog Jumbo',        description: 'Maxicombo Mix Jumbo con Hot Dog Jumbo' },
    { id: 'trad-t46', title: 'Maxicombo Mix Jumbo — Caramelo + Hot Dog',   description: 'Maxicombo Mix Jumbo con Palomitas Caramelo y Hot Dog Jumbo' },
    { id: 'trad-t47', title: 'Maxicombo Mix Jumbo — Doritos + Tajín',      description: 'Maxicombo Mix Jumbo con Palomitas Doritos y Nachos Tajín' },
    { id: 'trad-t48', title: 'Maxicombo Mix Jumbo — Cheetos + Tajín',      description: 'Maxicombo Mix Jumbo con Palomitas Cheetos y Nachos Tajín' },
    { id: 'trad-t49', title: 'Maxicombo Mix — Cheetos + Extra Queso',      description: 'Maxicombo Mix con Palomitas Cheetos y extra queso' },
    { id: 'trad-t50', title: 'Maxicombo Mix — Hot Dog Jumbo',              description: 'Maxicombo Mix con Hot Dog Jumbo' },
  ],
};

// ── Smoke Test — pool de 152 casos individuales para selección aleatoria ──────
const SMOKE_POOL: IndividualTest[] = [
  ...SUITE_TESTS['asientos'],
  ...SUITE_TESTS['flujo-completo'],
  ...SUITE_TESTS['checkout'],
  ...SUITE_TESTS['alimentos-atmosfera'],
  ...SUITE_TESTS['alimentos-coffee'],
  ...SUITE_TESTS['alimentos-micine'],
  ...SUITE_TESTS['alimentos-tradicional'],
  ...SUITE_TESTS['alimentos-vip'],
]

/**
 * Devuelve `count` casos elegidos al azar del pool completo.
 * Usa Fisher-Yates shuffle; cada llamada produce una selección diferente.
 */
export function getRandomSmokeTests(count = 50): IndividualTest[] {
  const pool = [...SMOKE_POOL]
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[pool[i], pool[j]] = [pool[j], pool[i]]
  }
  return pool.slice(0, Math.min(count, pool.length))
}
