/**
 * The My account area: profile, orders, wish list, addresses, sign-in & security, notifications and help.
 * It is separate from My studio (which is only about learning), and is reached from the avatar menu.
 */
export function accountNav(nav) {
    const n = nav || {};
    return [
        { href: '/user/my-account', label: 'Profile', icon: 'fa-user', title: 'Profile',
            subtitle: 'View and update your personal details and how others see you on ArtistikCity.' },
        { href: '/user/orders', label: 'Your orders', icon: 'fa-shopping-bag', count: n.orders, title: 'Your orders',
            subtitle: 'Courses, workshops, original artworks and commissions you have ordered.', match: /^\/user\/(orders|order-history)/ },
        { href: '/user/wishlist', label: 'Wish list', icon: 'fa-heart-o', count: n.wishlist, title: 'Your wish list',
            subtitle: 'Courses, workshops and original artworks you saved for later.' },
        { href: '/user/my-address', label: 'Addresses', icon: 'fa-map-marker', title: 'Addresses',
            subtitle: 'Billing and shipping addresses used at checkout.' },
        { href: '/user/linked-accounts', label: 'Login & security', icon: 'fa-shield', title: 'Login & security',
            subtitle: 'Choose how you sign in. Everything here belongs to one account, however you log in.', match: /^\/user\/(linked-accounts|change-password)/ },
        { href: '/user/notifications', label: 'Notifications', icon: 'fa-bell-o', title: 'Notifications',
            subtitle: 'Choose which e-mails and messages you receive.', match: /^\/user\/(notifications|language)/ },
        { href: '/user/help', label: 'Help & support', icon: 'fa-question-circle-o', title: 'Help & support',
            subtitle: 'Answers to common questions and ways to reach the studio.' },
    ];
}

export const ACCOUNT_PATHS = /^\/user\/(my-account|my-address|change-password|order-history|orders|notifications|linked-accounts|language|wishlist|help)(\/|$)/;

export function activeAccountItem(path, nav) {
    const items = accountNav(nav);
    return items.find((i) => (i.match ? i.match.test(path) : path.startsWith(i.href))) || items[0];
}
