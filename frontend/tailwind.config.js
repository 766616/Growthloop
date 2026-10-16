/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: { DEFAULT: '#1B2A4A', 50: '#EEF1F6', 100: '#D9DFEA', 200: '#B7C0CF', 300: '#8A97B0', 400: '#64739A', 500: '#43557A', 600: '#334566', 700: '#24365C', 800: '#1F3052', 900: '#121D34' },
        chalk: { DEFAULT: '#F3F5F1', 200: '#E4E8E1', 300: '#D2D8CE' },
        marigold: { DEFAULT: '#F2A93B', 50: '#FEF5E7', 100: '#FCE6C2', 600: '#D98B17', 700: '#A9690C' },
        sage: { DEFAULT: '#5E8C6A', 50: '#EDF4EF', 100: '#D3E4D8', 700: '#43664C' },
        coral: { DEFAULT: '#E0664F', 50: '#FCEDEA', 100: '#F8D3CB', 700: '#B2452F' },
        slateink: '#4A5568',
      },
      fontFamily: {
        display: ['"Bricolage Grotesque"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Figtree', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif'],
      },
      boxShadow: {
        lift: '0 1px 0 rgba(27,42,74,0.06), 0 8px 24px -12px rgba(27,42,74,0.25)',
      },
    },
  },
  plugins: [],
}
