import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './app/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}'
  ],
  theme: {
    extend: {
      colors: {
        background: '#0e0e12',
        surface: '#17171f',
        elevated: '#21212c',
        primary: '#7c4dff',
        accent: '#00e5c3',
        danger: '#ff5c6c',
        muted: '#9e9eaf'
      }
    }
  },
  plugins: []
}

export default config
