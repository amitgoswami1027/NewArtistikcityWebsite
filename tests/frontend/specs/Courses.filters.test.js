/**
 * /courses (frontend/resources/js/Pages/Courses.js): the filters change the list, and every change is mirrored into
 * the address bar with history.replaceState - no reload, no extra history entry, Inertia's history.state untouched -
 * so a refresh, Back/Forward or a shared link shows the same view.
 */
import React from 'react';
import { render, screen, fireEvent, within, cleanup } from '@testing-library/react';
import { usePage } from '@inertiajs/inertia-react';
import Courses from '@/Pages/Courses';

// the page's surroundings are not under test: a plain layout, a card that prints the title, a fixed age list
jest.mock('@/Components/Site/SiteLayout', () => {
    const React = require('react');
    return { __esModule: true, default: ({ children }) => React.createElement('main', null, children) };
});
jest.mock('@/Components/Site/CourseCard', () => {
    const React = require('react');
    return { __esModule: true, default: ({ course }) => React.createElement('article', { 'data-testid': 'course-card' }, course.title) };
});
jest.mock('@/Components/Site/SiteHeader', () => ({
    __esModule: true,
    useAgeGroups: () => ['Sub-junior - 8 to 12 yrs', 'Junior - 12 to 15 yrs', 'Adult - 16 yrs and above'],
}));

const COURSES = [
    { id: 1, title: 'Watercolour Landscapes for Beginners', course_type_id: 1, medium_id: 1, skill_id: 1, genre_id: '1', age_group: 'Junior - 12 to 15 yrs', students: 40, positive: 9, prices: [{ price_inr: 4999 }] },
    { id: 2, title: 'Pencil Sketching Essentials', course_type_id: 1, medium_id: 2, skill_id: 1, genre_id: '2', age_group: 'Sub-junior - 8 to 12 yrs', students: 25, positive: 7, prices: [{ price_inr: 2999 }] },
    { id: 3, title: 'Oil Painting Foundations', course_type_id: 1, medium_id: 3, skill_id: 2, genre_id: '1,3', age_group: 'Adult - 16 yrs and above', students: 12, positive: 8, prices: [{ price_inr: 7999 }] },
    { id: 4, title: 'Weekend Watercolour Florals Workshop', course_type_id: 2, medium_id: 1, skill_id: 1, genre_id: '3', age_group: 'Junior - 12 to 15 yrs', students: 60, positive: 10, prices: [{ price_inr: 1499 }] },
];

const PROPS = {
    courses: { data: COURSES },
    mediums: [{ id: 1, name: 'Watercolour' }, { id: 2, name: 'Pencil' }, { id: 3, name: 'Oil' }],
    genres: [{ id: 1, name: 'Landscape' }, { id: 2, name: 'Still life' }, { id: 3, name: 'Florals' }],
    skills: [{ id: 1, name: 'Beginner' }, { id: 2, name: 'Intermediate' }],
    type: 'All',
    filters: { medium: '', genre: '', skill: '', age: '', q: '' },
};

/** Puts the browser at a URL (with an Inertia-like history.state) and renders the page with matching server props. */
function open(search = '?type=all', filters = {}) {
    window.history.replaceState({ component: 'Courses', url: '/courses' + search }, '', '/courses' + search);
    usePage.mockReturnValue({ props: { ...PROPS, filters: { ...PROPS.filters, ...filters } } });
    return render(<Courses />);
}

const titles = () => screen.queryAllByTestId('course-card').map((c) => c.textContent);
const params = () => new URLSearchParams(window.location.search);

/** Age groups are visible pills; Format, Level and Subject sit in the "More filters" drawer. */
const age = (label) => within(screen.getByRole('group', { name: 'Age group' })).getByRole('button', { name: label });
function openDrawer() {
    fireEvent.click(screen.getByRole('button', { name: /More filters/ }));
    return screen.getByRole('dialog', { name: 'More filters' });
}

afterEach(() => {
    cleanup();
    jest.restoreAllMocks();
});

describe('Courses page filters', () => {
    test('lists every course before any filter is chosen', () => {
        open();
        expect(titles()).toHaveLength(4);
        expect(screen.getByText(/4 courses/)).toBeInTheDocument();
        expect(window.location.search).toBe('?type=all');
    });

    test('an age group narrows the list and is written into the URL', () => {
        open();
        fireEvent.click(age('Junior - 12 to 15 yrs'));

        expect(titles()).toEqual(['Weekend Watercolour Florals Workshop', 'Watercolour Landscapes for Beginners']);
        expect(params().get('age')).toBe('Junior - 12 to 15 yrs');
        expect(params().get('type')).toBe('all');
        expect(age('Junior - 12 to 15 yrs')).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'Remove age filter Junior - 12 to 15 yrs' })).toBeInTheDocument();
    });

    test('two age groups show both, joined with a comma in the URL', () => {
        open();
        fireEvent.click(age('Junior - 12 to 15 yrs'));
        fireEvent.click(age('Adult - 16 yrs and above'));

        expect(titles()).toHaveLength(3);
        expect(params().get('age')).toBe('Adult - 16 yrs and above,Junior - 12 to 15 yrs');
    });

    test('search text filters by title and goes into the URL as q', () => {
        open();
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'pencil' } });

        expect(titles()).toEqual(['Pencil Sketching Essentials']);
        expect(params().get('q')).toBe('pencil');
    });

    test('the sort order is kept in the URL (the default one is left out)', () => {
        open();
        fireEvent.change(screen.getByLabelText('Sort by'), { target: { value: 'price-asc' } });

        expect(titles()).toEqual([
            'Weekend Watercolour Florals Workshop', 'Pencil Sketching Essentials', 'Watercolour Landscapes for Beginners', 'Oil Painting Foundations',
        ]);
        expect(params().get('sort')).toBe('price-asc');

        fireEvent.change(screen.getByLabelText('Sort by'), { target: { value: 'popular' } });
        expect(params().has('sort')).toBe(false);
    });

    test('a medium tab and the Format filter combine, and the URL holds both', () => {
        open();
        fireEvent.click(within(screen.getByRole('navigation', { name: 'Mediums' })).getByRole('button', { name: 'Watercolour' }));
        fireEvent.click(within(openDrawer()).getByLabelText(/^Workshops/));
        fireEvent.keyDown(document, { key: 'Escape' });

        expect(titles()).toEqual(['Weekend Watercolour Florals Workshop']);
        expect(params().get('medium')).toBe('1');
        expect(params().get('type')).toBe('workshop');
    });

    test('a shared link opens with the same filters ticked and the same results', () => {
        open('?type=all&age=Sub-junior%20-%208%20to%2012%20yrs&sort=newest', { age: 'Sub-junior - 8 to 12 yrs' });

        expect(titles()).toEqual(['Pencil Sketching Essentials']);
        expect(screen.getByLabelText('Sort by')).toHaveValue('newest');
        expect(age('Sub-junior - 8 to 12 yrs')).toHaveAttribute('aria-pressed', 'true');
        expect(params().get('age')).toBe('Sub-junior - 8 to 12 yrs');
        expect(params().get('sort')).toBe('newest');
    });

    test('Clear filters empties the list filters and the URL', () => {
        open();
        fireEvent.click(age('Adult - 16 yrs and above'));
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'oil' } });
        expect(titles()).toEqual(['Oil Painting Foundations']);

        fireEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]);

        expect(titles()).toHaveLength(4);
        expect(window.location.search).toBe('?type=all');
    });

    test('no result shows the empty state instead of a blank grid', () => {
        open();
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'no such course' } });

        expect(titles()).toHaveLength(0);
        expect(screen.getByRole('heading', { name: 'No courses match your filters' })).toBeInTheDocument();
        expect(params().get('q')).toBe('no such course');
    });

    test('URL updates replace the entry: no new history entries, Inertia state kept, no page load', () => {
        open();
        const before = window.history.length;
        const state = window.history.state;
        const replace = jest.spyOn(window.history, 'replaceState');
        const push = jest.spyOn(window.history, 'pushState');

        fireEvent.click(age('Junior - 12 to 15 yrs'));
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'water' } });

        expect(push).not.toHaveBeenCalled();
        expect(replace).toHaveBeenCalled();
        for (const call of replace.mock.calls) expect(call[0]).toEqual(state);
        expect(window.history.length).toBe(before);
        expect(window.location.pathname).toBe('/courses');
    });

    test('search forgives typos and accents', () => {
        open();
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'pencl skeching' } });
        expect(titles()).toEqual(['Pencil Sketching Essentials']);
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'watercolor' } });
        expect(titles()).toHaveLength(2);
    });

    test('zero results offer the one change that brings courses back', () => {
        open();
        fireEvent.click(age('Adult - 16 yrs and above'));
        fireEvent.change(screen.getByLabelText('Search in results'), { target: { value: 'pencil' } });
        expect(titles()).toHaveLength(0);
        fireEvent.click(screen.getByRole('button', { name: /^Remove age “Adult/ }));
        expect(titles()).toEqual(['Pencil Sketching Essentials']);
    });

    test('the drawer shows counts per option and closes with Escape', () => {
        open();
        const d = openDrawer();
        expect(within(d).getByRole('group', { name: 'Format' })).toBeInTheDocument();
        const format = within(d).getByRole('group', { name: 'Format' });
        expect(within(format).getByLabelText(/^Workshops.*, 1 course$/)).toBeInTheDocument();       // one workshop
        expect(within(format).getByLabelText(/^Live courses.*, 3 courses$/)).toBeInTheDocument();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).toBeNull();
    });
});
