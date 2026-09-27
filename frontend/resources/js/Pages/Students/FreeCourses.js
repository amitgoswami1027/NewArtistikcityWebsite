import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import Pagination from '@/Components/Pagination';
import DataTable from 'react-data-table-component';

export default function FreeCourses(props) {
    const { FreeCourses } = usePage().props;

    const columns = [
        { name: 'Title', selector: row => row.title,},
    ];
    
    const data = [];
    FreeCourses.data.map((courses, i) => {
        data.push({ 
            title: courses.title, 
        })
    });
console.log(FreeCourses);
    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Free Courses" />
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        {/* <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="accountPage p30">
                                <DataTable 
                                    pagination
                                    columns={columns}
                                    data={data}
                                />
                            </div>
                        </main> */}
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="homepage p30">
                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">FREE COURSES</h6>
                                    {FreeCourses.data.length != 0 && (
                                        <div className="row">
                                            {FreeCourses.data.map((free_course, i) => {
                                                return (
                                                    <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                        <div className="course_img">
                                                            <img src={(`/storage/uploads/free-courses/${free_course.id}/${free_course.photo}`)} alt={free_course.title} />
                                                        </div>
                                                        <div className="course_info me-auto">
                                                            <h3>{free_course.title}</h3>
                                                            <p dangerouslySetInnerHTML={{__html: free_course.description}}></p>
                                                        </div>
                                                        <div className="course_actn">
                                                            <a href={route('user.free.course.home', free_course.id)} className="btn btn-primary">COURSE DETAILS</a>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                    <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                        <Pagination links={FreeCourses.links} />
                                    </div>
                                    {FreeCourses.data.length == 0 && (
                                        <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                            There is no free course available for you.
                                        </div>
                                    )}
                                </div>
                            </div>
                        </main>
                    </div>
                </div>
                {/* Main Section */}
            </Authenticated>
        </>
    );
}
