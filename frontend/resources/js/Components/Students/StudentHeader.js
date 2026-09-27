import React from 'react';
import ReactDOM from "react-dom";
import {Link, InertiaLink} from "@inertiajs/inertia-react";
import Example from "@/Components/Example";

export default function StudentHeader({ auth, header, children }){
    return (
        <header className="header sticky-top">
            <div className="container-fluid">
                <div className="d-flex flex-wrap align-items-center justify-content-between">
                    <a href={route('welcome')} className="d-flex align-items-center"><img src="/assets/user/images/site-logo.svg" alt=""/></a>
                    <div className="ml-auto d-flex align-items-center justify-content-center">
                        <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse"
                                data-bs-target="#headerMenu" aria-controls="headerMenu" aria-expanded="false"
                                aria-label="Toggle Navigation">
                            <span className="navbar-toggler-icon"></span>
                        </button>
                        <nav id="headerMenu" className="d-md-block collapse">
                            <ul className="nav justify-content-center mb-0">
                                <li><Link href={route('user.account')}>My Account</Link></li>
                                {/* <li className="dropdown">
                                    <Link href="#" className="dropdown-toggle" id="dropdownMenu" data-bs-toggle="dropdown" aria-expanded="false">My Classroom</Link>
                                    <ul className="dropdown-menu" aria-labelledby="dropdownMenu">
                                        <li><Link className="dropdown-item" href="#">Classroom List 1</Link></li>
                                        <li><Link className="dropdown-item" href="#">Classroom List 2</Link></li>
                                        <li><Link className="dropdown-item" href="#">Classroom List 3</Link></li>
                                    </ul>
                                </li> */}
                                <li><Link href={route('user.help')}>Help</Link></li>
                            </ul>
                        </nav>
                        <div className="text-end d-flex align-items-center">
                            <a href={route('student.profile', auth.user.slug)} target='_blank'>{auth.user.name}</a>
                            &nbsp;&nbsp;
                            {auth.user.profile_photo !== null && (
                                <img src={(`/storage/uploads/students/${auth.user.id}/${auth.user.profile_photo}`)} alt="mdo" className="rounded-circle" height="50" />
                            )}
                            <Link 
                                href={route('logout')}
                                className="logout"
                                method="post"
                            >
                            <img src="/assets/user/images/logout-red.svg" alt=""/>
                            </Link>
                            {/* <Link href={route('logout')} className="logout">
                                <img src="/assets/user/images/logout-red.svg" alt=""/>
                            </Link> */}
                        </div>
                    </div>
                </div>
            </div>
        </header>
    );
}
