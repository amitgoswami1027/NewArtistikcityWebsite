require('@testing-library/jest-dom');

// Ziggy's route() helper is a global on the real pages
global.route = (name) => (name === 'courses' ? '/courses' : '/' + String(name));
