import React from 'react';
import ReactDOM from "react-dom";
import {Link, usePage, InertiaLink} from "@inertiajs/inertia-react";
import Example from "@/Components/Example";

export default function StudentNavigation({ auth, header, children }){
    const {url, component}  = usePage()
    return (
        <>
            <div className="col-md-2 px-0 sidebar">
                <div className="menuBtn">MENU <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button></div>
                <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                    <div className=" position-sticky">
                        <ul className="sidebarnav">
                            <li> <a className={url === '/user/dashboard' ? 'active' : ''} href={route('user.dashboard')}><span className="icon"><img src="/assets/user/images/dashboard-icon.svg" alt="" /></span> Home </a> </li>
                            {/*<li> <a className={url === '/user/free-courses' ? 'active' : ''} href={route('user.free.courses')}><span className="icon"><img src="/assets/user/images/courses-icon.svg" alt="" /></span> Free Courses </a> </li>*/}
                            <li> <a className={url === '/user/my-courses' ? 'active' : ''} href={route('user.courses')}><span className="icon"><img src="/assets/user/images/courses-icon.svg" alt="" /></span> My Courses </a> </li>
                            <li> <a className={url === '/user/my-workshop' ? 'active' : ''} href={route('user.workshop')}><span className="icon"><img src="/assets/user/images/workshop-icon.svg" alt="" /></span> My Workshop </a> </li>
                            <li> <a className={url === '/user/my-account' ? 'active' : ''} href={route('user.account')}><span className="icon"><img src="/assets/user/images/account-icon.svg" alt="" /></span> My Account </a> </li>
                            <li> <a className={url === '/user/help' ? 'active' : ''} href={route('user.help')}><span className="icon"><img src="/assets/user/images/help-icon.svg" alt="" /></span> Help </a> </li>
                            <li>
                                <InertiaLink href={route('logout')} method="post">
                                    <span className="icon"><img src="/assets/user/images/logout-white.svg" alt="" /></span> Logout
                                </InertiaLink>
                            </li>
                        </ul>
                    </div>
                </nav>
            </div>
        </>
    );
}
