import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // CooBS Staff Console — Corporate Navy Palette
        canvas: '#F0F4F8',
        surface: '#FFFFFF',
        sidebar: '#1E3A5F',
        'sidebar-hover': '#264A73',
        border: '#E2E8F0',
        'border-strong': '#CBD5E1',

        navy: {
          50: '#EEF2F7',
          100: '#D5DDE8',
          200: '#A8B8CC',
          300: '#7B93B0',
          400: '#4E6E94',
          500: '#1E3A5F',
          600: '#182F4D',
          700: '#12243B',
          800: '#0C1929',
          900: '#060E17',
        },

        accent: {
          DEFAULT: '#2563EB',
          hover: '#1D4ED8',
          light: '#DBEAFE',
          50: '#EFF6FF',
        },

        success: {
          DEFAULT: '#10B981',
          light: '#D1FAE5',
          dark: '#059669',
        },
        warning: {
          DEFAULT: '#F59E0B',
          light: '#FEF3C7',
          dark: '#D97706',
        },
        error: {
          DEFAULT: '#EF4444',
          light: '#FEE2E2',
          dark: '#DC2626',
        },
        info: {
          DEFAULT: '#3B82F6',
          light: '#DBEAFE',
        },

        // Role badge colors
        teller: {
          text: '#0F766E',
          bg: '#CCFBF1',
        },
        admin: {
          text: '#6D28D9',
          bg: '#EDE9FE',
        },

        // Status pill colors
        status: {
          active: '#10B981',
          'active-bg': '#D1FAE5',
          frozen: '#EF4444',
          'frozen-bg': '#FEE2E2',
          pending: '#F59E0B',
          'pending-bg': '#FEF3C7',
          closed: '#94A3B8',
          'closed-bg': '#F1F5F9',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      fontSize: {
        '2xs': ['0.6875rem', { lineHeight: '1rem' }],   // 11px
        xs: ['0.8125rem', { lineHeight: '1.125rem' }],   // 13px
      },
      borderRadius: {
        card: '12px',
        input: '8px',
        pill: '9999px',
      },
      boxShadow: {
        card: '0 1px 3px rgba(0, 0, 0, 0.06)',
        'card-hover': '0 4px 12px rgba(0, 0, 0, 0.08)',
        modal: '0 20px 60px rgba(0, 0, 0, 0.15)',
      },
      spacing: {
        sidebar: '64px',
        header: '56px',
      },
    },
  },
  plugins: [],
};

export default config;
