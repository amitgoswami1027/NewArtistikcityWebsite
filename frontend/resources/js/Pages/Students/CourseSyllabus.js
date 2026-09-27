import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import CourseNavigation from '@/Components/Students/CourseNavigation';

export default function CourseHome(props) {
    const { CourseModule, CourseNav } = usePage().props;
    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Course Home" />

                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <CourseNavigation courseNav={CourseNav} />
                        {/* <div className="col-md-2 px-0 sidebar coursesidebar">
                            <div className="courseTitle">Charcoal Drawing Foundation Course <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button></div>
                            <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                                <div className=" position-sticky">
                                    <ul className="coursenav">
                                        <li> <a href="#">COURSE HOME</a> </li>
                                        <li> <a className="active" href="#">SYLLABUS</a> </li>
                                        <li className="dropdown"> <button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">MODULES</button>
                                            <div id="collapseOne" className="accordion-collapse collapse show">
                                                <ul>
                                                    <li><a href="#">1. Before we start drawing</a></li>
                                                    <li><a href="#">2. Principles of good drawing</a></li>
                                                    <li><a href="#">3. Object Drawing</a></li>
                                                </ul>
                                            </div>
                                        </li>
                                    </ul>
                                </div>
                            </nav>
                        </div> */}
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="siteBreadcrumb">
                                <nav aria-label="breadcrumb">
                                    <ol className="breadcrumb">
                                        <li className="breadcrumb-item"><a href={route('user.dashboard')}>My Classroom</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.courses')}>Course</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.course.home', CourseNav.course_id)}>{CourseNav.course_title}</a></li>
                                        <li className="breadcrumb-item active" aria-current="page">Syllabus</li>
                                    </ol>
                                </nav>
                            </div>

                            <div className="aboutCourse p30">
                                <div className="row">
                                    <div className="col-md-10 px-0 pe-5">
                                        <h2>{CourseNav.course_title}</h2>
                                        <p>{CourseNav.course_sub_title}</p>
                                    </div>
                                    <div className="col-md-2 d-flex justify-content-end px-0">
                                        {/* <ul className="crsList">
                                            <li><img src="assets/images/module-icon.svg" alt="" /> 3 Module</li>
                                            <li><img src="assets/images/project-icon.svg" alt="" /> 1 Project</li>
                                            <li><img src="assets/images/week-icon.svg" alt="" /> 4 Weeks</li>
                                        </ul> */}
                                    </div>
                                </div>
                            </div>

                            <div className="todoSctn allSyllabus p30">

                                <div className="courseSection">
                                    <h6 className="sectionTitle">COURSE SYLLABUS</h6>
                                    <div className="row">
                                        {CourseModule.map((module, i) => { return (
                                            <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                <div className="course_info me-auto">
                                                    <h3>Module {i+1}: <a href={route('user.course.modules', module.id)}>{module.module_title}</a></h3>
                                                    {module.lessons.map((lesson, j) => { return (
                                                        <h5>Lesson {j+1}: {lesson.lesson_title}</h5>
                                                    );})}
                                                    <h6>{module.module_duration}</h6>
                                                </div>
                                            </div>
                                        );})}
                                    </div>
                                </div>
                            </div>
                            <div className="helpBotm">Need Help? Check on FAQ or contact <a href={route('user.help')}>here</a></div>
                        </main>
                    </div>
                </div>
                {/* Main Section */}

            </Authenticated>
        </>
    );
}