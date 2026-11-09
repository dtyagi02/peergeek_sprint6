/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f4f7f5",
          100: "#e5ede8",
          500: "#1a4732",
          600: "#0d281c",
          700: "#06110C",
        },
        charcoal: {
          DEFAULT: "#171717",
          50: "#2d2d2d",
          100: "#262626",
          200: "#1f1f1f",
          900: "#171717",
        },
        forest: {
          DEFAULT: "#06110C",
          50: "#1a3528",
          100: "#13281e",
          200: "#0d1c15",
          800: "#091710",
          900: "#06110C",
        },
        crimson: {
          DEFAULT: "#A31C1B",
          50: "#fdf2f2",
          100: "#fde8e8",
          500: "#c52221",
          600: "#A31C1B",
          700: "#881716",
          800: "#6d1211",
          900: "#520e0d",
        },
        surface: {
          50: "#f8fafc",
          100: "#f1f5f9",
          200: "#e2e8f0",
        },
        ink: {
          700: "#334155",
          900: "#0f172a",
        },
        danger: {
          50: "#fef2f2",
          600: "#dc2626",
        },
      },
      spacing: {
        18: "4.5rem",
        22: "5.5rem",
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};
