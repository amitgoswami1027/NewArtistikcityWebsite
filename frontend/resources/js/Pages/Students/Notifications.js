import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';

export default function Notifications(props) {
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
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />

                                <div className="accountDtl">
                                    <div className="accountEdit">
                                        <h3>Notifications</h3>

                                        <div className="accountForm">
                                            
                                        </div>

                                    </div>

                                    <div className="accountActn">
                                        <button className="btn btn-secondary">Cancel</button>
                                        <button className="btn btn-primary">SAve</button>
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
