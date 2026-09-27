const mix = require('laravel-mix');

// The legacy style.css sets `html { font-size: 62.5% }` (1rem = 10px). Tailwind sizes are in rem, so the
// scoped commission utilities are converted to px (1rem = 16px) to render at their intended size.
const remToPx = () => ({
    postcssPlugin: 'rem-to-px',
    Declaration(decl) {
        if (decl.value && decl.value.includes('rem')) {
            decl.value = decl.value.replace(/(-?\d*\.?\d+)rem\b/g, (m, n) => `${parseFloat((parseFloat(n) * 16).toFixed(3))}px`);
        }
    },
});
remToPx.postcss = true;

/*
 |--------------------------------------------------------------------------
 | ArtistikCity front-end build (React + Inertia.js)
 |--------------------------------------------------------------------------
 | Laravel Mix is a stand-alone webpack wrapper (no PHP required). The bundle is
 | written straight into the Spring Boot static folder, which is served at "/":
 |     ../src/main/resources/static/js/app.js   and   /css/app.css
 |
 |   npm install
 |   npm run dev        (development build)
 |   npm run watch      (rebuild on change)
 |   npm run prod       (minified production build)
 */
mix.setPublicPath('../src/main/resources/static');

mix.js('resources/js/app.js', 'js')
    .react()
    .postCss('resources/css/app.css', 'css', [
        require('postcss-import'),
        require('tailwindcss'),
        require('autoprefixer'),
    ])
    .postCss('resources/css/commission.css', 'css', [
        require('tailwindcss'),
        remToPx(),
        require('autoprefixer'),
    ])
    .webpackConfig(require('./webpack.config'));

// the Spring templates reference /js/app.js directly, so no mix-manifest versioning is used
mix.options({ manifest: false });
