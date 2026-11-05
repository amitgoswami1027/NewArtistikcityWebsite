import React from 'react';
import ReactDOM from "react-dom";
import { Link, usePage } from "@inertiajs/inertia-react";
import Example from "@/Components/Example";

export default function MyAccountNavigation({ auth, header, children }) {
    const { url, component } = usePage()
    return (
        <>
            <div className="accountMenu">
                <h5>MY Account</h5>
                <ul>
                    <li className={url === '/user/my-account' ? 'active' : ''}><Link href={route('user.account')}>Personal Information</Link></li>
                    <li className={url === '/user/my-address' ? 'active' : ''}><Link href={route('user.my.address')}>My Address</Link></li>
                    <li className={url === '/user/linked-accounts' ? 'active' : ''}><Link href="/user/linked-accounts">Login &amp; Security</Link></li>
                    {auth.user.has_password && (
                        <li className={url === '/user/change-password' ? 'active' : ''}><Link href={route('user.change.password')}>Change Password</Link></li>
                    )}
                    <li className={url === '/user/order-history' ? 'active' : ''}><Link href={route('user.order.history')}>Order History</Link></li>
                    <li className={url === '/user/notifications' ? 'active' : ''}><Link href={route('user.notifications')}>Notifications</Link></li>
                </ul>
                {/* <h5>ENROLLMENT</h5>
                <ul>
                    <li><a href="#">Subscriptions & Billing</a></li>
                    <li><a href="#">Courses</a></li>
                </ul> */}
            </div>
        </>
    );
}
