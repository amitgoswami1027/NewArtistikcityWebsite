// Unit tests for frontend/resources/js. Run from this folder: npm install && npm test
const path = require('path');

const SRC = path.resolve(__dirname, '../../frontend/resources/js');

module.exports = {
    rootDir: __dirname,
    roots: ['<rootDir>/specs'],
    testEnvironment: 'jsdom',
    testEnvironmentOptions: { url: 'http://localhost/courses?type=all' },
    setupFilesAfterEnv: ['<rootDir>/setup.js'],
    // the page sources live outside this folder: compile them with an inline Babel config (no .babelrc needed,
    // so the Laravel Mix build is not affected)
    transform: {
        '\\.[jt]sx?$': ['babel-jest', {
            configFile: false,
            babelrc: false,
            presets: [
                ['@babel/preset-env', { targets: { node: 'current' } }],
                ['@babel/preset-react', { runtime: 'automatic' }],
            ],
        }],
    },
    moduleNameMapper: {
        '^@/(.*)$': `${SRC}/$1`,
        // one React for the tests and the page (the page's folder may have its own node_modules)
        '^react$': '<rootDir>/node_modules/react',
        '^react/jsx-runtime$': '<rootDir>/node_modules/react/jsx-runtime.js',
        '^react-dom$': '<rootDir>/node_modules/react-dom',
        '^@inertiajs/inertia-react$': '<rootDir>/__mocks__/inertia-react.js',
    },
    clearMocks: true,
};
