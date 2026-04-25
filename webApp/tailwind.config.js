const path = require('path');

/** @type {import('tailwindcss').Config} */
module.exports = {
  // Use path.resolve with __dirname to be portable and absolute at runtime
  content: [
    path.resolve(__dirname, "./src/jsMain/kotlin/**/*.kt"),
    path.resolve(__dirname, "./src/jsMain/resources/**/*.html"),
    path.resolve(__dirname, "./build/compileSync/js/main/developmentExecutable/kotlin/*.js"),
    path.resolve(__dirname, "./build/compileSync/js/main/productionExecutable/kotlin/*.js"),
  ],
  important: true,
  theme: {
    extend: {
      colors: {
        'multipaz-dark': '#0D1B2A',
        'multipaz-blue': '#4285F4',
      }
    },
  },
  plugins: [],
}
