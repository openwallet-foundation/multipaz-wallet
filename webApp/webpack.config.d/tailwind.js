const path = require('path');

// Configure postcss-loader for Tailwind CSS
const cssRule = config.module.rules.find(r => r.test && r.test.toString().includes('css'));
if (cssRule) {
    const use = cssRule.use;
    const cssLoaderIndex = use.findIndex(u => u.loader && u.loader.includes('css-loader'));
    if (cssLoaderIndex !== -1) {
        // Based on logs: __dirname is build/js/packages/multipaz-wallet-webApp
        // Project root is 4 levels up from there:
        // 1: packages, 2: js, 3: build, 4: root
        const tailwindConfigPath = path.resolve(__dirname, "../../../../webApp/tailwind.config.js");
        use.splice(cssLoaderIndex + 1, 0, {
            loader: 'postcss-loader',
            options: {
                postcssOptions: {
                    plugins: [
                        ["tailwindcss", { config: tailwindConfigPath }],
                        "autoprefixer"
                    ]
                }
            }
        });
    }
}
