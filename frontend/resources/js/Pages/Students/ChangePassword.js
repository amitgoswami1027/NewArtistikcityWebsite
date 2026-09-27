import { Inertia } from '@inertiajs/inertia';
import React, {useState} from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';

export default function ChangePassword(props) {
    const [values, setValues] = useState({
        password: '',
        new_password: '',
        confirm_new_password: ''
    })

    function handleChange(e) {
        const key = e.target.id;
        const value = e.target.value
        setValues(values => ({
            ...values,
            [key]: value,
        }))
    }

    function handleSubmit(e) {
        e.preventDefault()
        Inertia.post(route('user.change.password.save'), values)
    }
    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Change Password" />
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation auth={props.auth} />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />

                                <div className="accountDtl">
                                    <form onSubmit={handleSubmit}>
                                        <div className="accountEdit">
                                            <h3>Change Password</h3>
                                            <div className="accountForm">
                                                <div className="form-group"><label>Current Password</label><input type="password" className="form-control" id='password' value={values.password} onChange={handleChange} placeholder="Password" /></div>
                                                <div className="form-group"><label>New Password</label><input type="password" className="form-control" id='new_password' value={values.new_password} onChange={handleChange} placeholder="New Passowrd" /></div>
                                                <div className="form-group"><label>Confirm New Password</label><input type="password" className="form-control" id='confirm_new_password' value={values.confirm_new_password} onChange={handleChange} placeholder="Confirm New Password" /></div>
                                            </div>
                                        </div>

                                        <div className="accountActn">
                                            <button className="btn btn-secondary">Cancel</button>
                                            <button type='submit' className="btn btn-primary">Save</button>
                                        </div>
                                    </form>
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
