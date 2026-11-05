// Inertia's React adapter, reduced to what the pages under test use. Each test sets the page props with
// usePage.mockReturnValue({ props: {...} }).
const React = require('react');

const usePage = jest.fn(() => ({ props: {} }));

function InertiaLink({ href, children, ...rest }) {
    return React.createElement('a', { href, ...rest }, children);
}

module.exports = { usePage, InertiaLink };
