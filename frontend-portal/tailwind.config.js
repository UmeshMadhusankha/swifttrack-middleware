/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./app/**/*.{js,jsx}', './components/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // The dark sidebar and the deep navy the brand sits on.
        ink: {
          900: '#0b1120',
          800: '#111a2e',
          700: '#1a2540',
          600: '#25324f',
          500: '#3a4767',
        },
        // SwiftTrack blue, used for the wordmark, primary buttons and links.
        brand: {
          50: '#eef4ff',
          100: '#d9e6ff',
          300: '#7ea8ff',
          400: '#4f86ff',
          500: '#2563eb',
          600: '#1d4ed8',
          700: '#1e40af',
        },
      },
      fontFamily: {
        sans: ['ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(16, 24, 40, 0.04), 0 4px 16px rgba(16, 24, 40, 0.06)',
        lift: '0 8px 30px rgba(16, 24, 40, 0.12)',
      },
      keyframes: {
        // The pulsing node on the client's saga timeline.
        beacon: {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(37, 99, 235, 0.55)' },
          '50%': { boxShadow: '0 0 0 10px rgba(37, 99, 235, 0)' },
        },
        rise: {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        beacon: 'beacon 1.8s ease-out infinite',
        rise: 'rise 0.25s ease-out both',
      },
    },
  },
  plugins: [],
};
