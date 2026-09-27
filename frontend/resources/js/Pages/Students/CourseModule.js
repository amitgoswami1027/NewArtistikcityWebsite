import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import CourseNavigation from '@/Components/Students/CourseNavigation';

export default function CourseHome(props) {
    const { CourseModule, CourseNav } = usePage().props;
    let lessonCompleted = 0;
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
                                        <li> <a href="#">SYLLABUS</a> </li>
                                        <li className="dropdown"> <button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">MODULES</button>
                                            <div id="collapseOne" className="accordion-collapse collapse show">
                                                <ul>
                                                    <li><a className="active" href="#">1. Before we start drawing</a></li>
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
                                        <li className="breadcrumb-item"><a href={route('user.course.home', CourseModule.course.id)}>{CourseModule.course.title}</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.course.syllabus', CourseModule.course_id)}>Syllabus</a></li>
                                        <li className="breadcrumb-item" aria-current="page">Modules</li>
                                        <li className="breadcrumb-item active" aria-current="page">{CourseModule.module_title}</li>
                                    </ol>
                                </nav>
                            </div>
                            <div className="aboutCourse p30">
                                <div className="row">
                                    <div className="col-md-10 px-0 pe-5">
                                        <h2>Module: {CourseModule.module_title}</h2>
                                    </div>
                                </div>
                            </div>
                            <div className="totalLesson">
                                <div className="row">
                                    <div className="col-md-12 px-0">
                                        <h3>Total Lessons {CourseModule.lessons.length}, 0 Completed</h3>
                                    </div>
                                </div>
                            </div>
                            <div className="todoSctn allModule p30">
                                <div className="courseSection">

                                    <h6 className="sectionTitle">LESSONS</h6>
                                    {CourseModule.lessons.length != 0 && (
                                    <div className="row">
                                        {CourseModule.lessons.map((lesson, i) => {
                                            return (
                                                <>
                                                    {lessonCompleted ? (
                                                        <div className="col-md-12 courseInfo">
                                                            <a href={route('user.course.module.lesson', lesson.module_id)} className="d-flex align-items-center justify-content-between">
                                                                <div className="course_info me-auto">
                                                                    <h5>LESSON {i+1}</h5>
                                                                    <h3>{lesson.lesson_title}</h3>
                                                                    <p className="pe-0"  dangerouslySetInnerHTML={{__html: lesson.lesson_description}}></p>
                                                                </div>
                                                                <div className="moduleActn">
                                                                    <span className="actnbtn"><i><img src="/assets/user/images/completed-icon.svg" /></i> COMPLETED</span>
                                                                </div>
                                                            </a>
                                                        </div>
                                                    ) : (
                                                        <div className="col-md-12 courseInfo">
                                                            <div className="course_info me-auto">
                                                                <h5>LESSON {i+1}</h5>
                                                                <h3>{lesson.lesson_title}</h3>
                                                                <p className="pe-0"  dangerouslySetInnerHTML={{__html: lesson.lesson_description}}></p>
                                                            </div>
                                                            <div className="moduleActn d-flex align-items-center justify-content-between mt-4">
                                                                <a href={route('user.course.module.lesson', lesson.module_id)} className="btn btn-primary">VIEW LESSON</a>
                                                                <span className="actnbtn border0"><i><img src="/assets/user/images/not-completed-icon.svg" /></i> NOT COMPLETED</span>
                                                            </div>
                                                        </div>
                                                    )}
                                                </>


                                            );
                                        })}
                                    </div>
                                    )}
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
