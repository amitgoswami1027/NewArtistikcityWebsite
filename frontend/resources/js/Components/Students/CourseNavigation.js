import React from 'react';
import ReactDOM from "react-dom";
import {Link, usePage, InertiaLink} from "@inertiajs/inertia-react";
import Example from "@/Components/Example";

export default function CourseNavigation({courseNav}){
    console.log(courseNav)
    return (
        <>
            {courseNav.length != 0 && (
            <div className="col-md-2 px-0 sidebar coursesidebar">
                <div className="courseTitle"> {courseNav.course_title} <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button></div>
                <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                    <div className=" position-sticky">
                        <ul className="coursenav">
                            <li> <a className="active" href={route('user.courses')}>COURSE HOME</a> </li>
                            <li> <a href={route('user.course.syllabus', courseNav.course_id)}>SYLLABUS</a> </li>
                            {courseNav.course_module.length != 0 && (
                            <li className="dropdown"> 
                                <button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">MODULES</button> 
                                <div id="collapseOne" className="accordion-collapse collapse show">
                                    <ul>
                                        {courseNav.course_module.map((module, i) => {
                                            return (
                                                <li><a href={route('user.course.modules', module.module_id)}>{i+1}. {module.module_title}</a></li>
                                            );
                                        })}
                                    </ul>
                                </div>
                            </li>
                            )}
                        </ul>
                    </div>
                </nav>
            </div>
            )}
        </>
    );
}
