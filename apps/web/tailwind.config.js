/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: "class",
  content: [
    "./app/**/*.{js,ts,jsx,tsx}",
    "./components/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: "#40f320",
        "background-light": "#f6f8f5",
        "background-dark": "#050505",
        "card-dark": "#0A0A0A",
        "error": "#DC143C",
      },
      fontFamily: {
        display: ["Space Grotesk", "sans-serif"],
        mono: ["Space Grotesk", "monospace"],
      },
      boxShadow: {
        neon: "0 0 20px rgba(64, 243, 32, 0.5), 0 0 10px rgba(64, 243, 32, 0.3)",
      },
    },
  },
  plugins: [],
};

