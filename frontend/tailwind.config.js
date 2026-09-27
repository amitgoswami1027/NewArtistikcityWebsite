const defaultTheme = require('tailwindcss/defaultTheme');

/*
 * The legacy style.css sets `html { font-size: 62.5% }`, which makes 1rem = 10px and would shrink
 * every rem-based Tailwind size. All rem values are converted to pixels (1rem = 16px) so the
 * commission pages render at the same scale as the rest of the site.
 */
const toPx = (v) => {
    if (typeof v === 'string') return v.replace(/(-?\d*\.?\d+)rem/g, (m, n) => `${parseFloat(n) * 16}px`);
    if (Array.isArray(v)) return v.map(toPx);
    if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, toPx(x)]));
    return v;
};
const pxTheme = (key) => (typeof defaultTheme[key] === 'function'
    ? (theme, utils) => toPx(defaultTheme[key](theme, utils))
    : toPx(defaultTheme[key]));

module.exports = {
    // JIT: only the classes actually used are generated (also in `npm run dev`).
    mode: 'jit',
    purge: [
        '../src/main/resources/templates/**/*.mustache',
        './resources/js/**/*.js',
    ],

    // Utilities are scoped to elements inside a `.tw` wrapper (e.g. the Artist Commission Portal),
    // so they never clash with the legacy style.css used by older pages.
    important: '.tw',

    theme: {
        spacing: pxTheme('spacing'),
        fontSize: pxTheme('fontSize'),
        lineHeight: pxTheme('lineHeight'),
        maxWidth: pxTheme('maxWidth'),
        borderRadius: pxTheme('borderRadius'),
        extend: {
            fontFamily: {
                sans: ['Plus Jakarta Sans', 'Nunito', ...defaultTheme.fontFamily.sans],
            },
            colors: {
                brand: { DEFAULT: '#e5156b', dark: '#b80f55', soft: '#fde7f0' },
                ink: '#111111',
            },
        },
    },

    variants: {
        extend: {
            opacity: ['disabled'],
            cursor: ['disabled'],
            backgroundColor: ['disabled'],
        },
    },

    plugins: [require('@tailwindcss/forms')],
};
