import React from 'react';
import ReactDOM from "react-dom";
import { Link, usePage, InertiaLink } from "@inertiajs/inertia-react";
import Example from "@/Components/Example";
import { Inertia } from "@inertiajs/inertia";

export default function IsLoggedIn({ auth }) {
    let isUserLoggedIn = false;
    let userName = "";
    if(auth.user){
        isUserLoggedIn = true;
        userName = auth.user.name;
    }
    // const buttonClick = (e) => {
    //     Inertia.visit(route('user.dashboard'), {
    //         method: 'get',
    //         data: {},
    //         replace: false,
    //         preserveState: false,
    //         preserveScroll: false,
    //       })
    // }
    return (
        <>
            {isUserLoggedIn && (
                <>
                    {/* <span className="">{userName}</span>  */}
                    <a as="button" href={route('user.dashboard')} className="btn" method="get">Dashboard</a>&nbsp;
                    <InertiaLink as="button" href={route('logout')} className="btn" method="post">Logout</InertiaLink>
                </>
            )}
            {!isUserLoggedIn && (
                <>
                    <a className="btn btn2" href={route('login')}>Login</a> <a className="btn" href={route('register')}>Register</a>    
                </>
            )}
        </>
    );
}
