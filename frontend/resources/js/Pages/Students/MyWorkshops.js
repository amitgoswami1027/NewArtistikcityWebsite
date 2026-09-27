import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import { COURSE_TAGS_COLOR } from '@/Components/Constants';

export default function MyWorkshops(props){
    const { myWorkshops } = usePage().props;
    const workshops = myWorkshops.filter((courses) => courses.course_type_id === 1)
    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Dashboard" />
                {/* <div className="py-12">
                <div className="max-w-7xl mx-auto sm:px-6 lg:px-8">
                    <div className="bg-white overflow-hidden shadow-sm sm:rounded-lg">
                        <div className="p-6 bg-white border-b border-gray-200">You're logged in!</div>
                    </div>
                </div>
            </div> */}
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="homepage p30">
                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">ENROLLED WORKSHOPS</h6>
                                    <div className="row">
                                        {workshops.map((my_workshops, i) => {
                                            return (
                                                <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                    <div className="course_img">
                                                        <img src={(`/storage/uploads/courses/${my_workshops.course_id}/${my_workshops.photo}`)} alt={my_workshops.title} />
                                                    </div>
                                                    <div className="course_info me-auto">
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{my_workshops.medium}</label>
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.age}`}}>{my_workshops.age_group}</label>
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.skill}`}}>{my_workshops.skill}</label>
                                                        <h3>{my_workshops.title}</h3>
                                                        <p>{my_workshops.sub_title}</p>
                                                        <h6>{my_workshops.duration} days workshop</h6>
                                                    </div>
                                                    <div className="course_actn">
                                                        <a href={route('user.workshop.home', my_workshops.order_id)} className="btn btn-primary">COURSE HOME</a>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                        {workshops.length == 0 && (
                                            <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                You have not enrolled for any workshops.
                                            </div>
                                        )}
                                    </div>
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
