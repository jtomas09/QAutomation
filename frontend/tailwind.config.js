/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'Consolas', 'monospace'],
      },
      colors: {
        navy: {
          950: '#020817',
          900: '#040816',
          800: '#070c1c',
          700: '#0c1226',
          600: '#131d38',
          500: '#1e2d55',
        },
        brand: {
          DEFAULT: '#6366f1',
          light: '#818cf8',
          dark: '#4f46e5',
        },
      },
      animation: {
        'float':         'float 3s ease-in-out infinite',
        'pulse-dot':     'pulseDot 2s ease-in-out infinite',
        'gradient-x':    'gradientX 6s ease infinite',
        'glow':          'glow 2s ease-in-out infinite alternate',
        'slide-in':      'slideIn 0.3s ease-out',
        'fade-in':       'fadeIn 0.4s ease-out',
      },
      keyframes: {
        float: {
          '0%,100%': { transform: 'translateY(0px) rotate(-5deg)' },
          '50%':     { transform: 'translateY(-12px) rotate(5deg)' },
        },
        pulseDot: {
          '0%,100%': { opacity: '1', transform: 'scale(1)' },
          '50%':     { opacity: '0.4', transform: 'scale(0.85)' },
        },
        gradientX: {
          '0%,100%': { backgroundPosition: '0% 50%' },
          '50%':     { backgroundPosition: '100% 50%' },
        },
        glow: {
          from: { boxShadow: '0 0 20px rgba(99,102,241,0.2)' },
          to:   { boxShadow: '0 0 40px rgba(99,102,241,0.5)' },
        },
        slideIn: {
          from: { opacity: '0', transform: 'translateX(-8px)' },
          to:   { opacity: '1', transform: 'translateX(0)' },
        },
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
      },
      boxShadow: {
        'glow-brand': '0 0 30px rgba(99,102,241,0.35)',
        'glow-sm':    '0 0 15px rgba(99,102,241,0.2)',
        'glow-green': '0 0 20px rgba(16,185,129,0.3)',
        'glow-red':   '0 0 20px rgba(244,63,94,0.3)',
        'card':       '0 4px 24px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.04)',
        'card-hover': '0 8px 40px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.06)',
      },
      backgroundImage: {
        'grid': "linear-gradient(rgba(99,102,241,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(99,102,241,0.04) 1px, transparent 1px)",
        'noise': "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E\")",
      },
    },
  },
  plugins: [],
}
