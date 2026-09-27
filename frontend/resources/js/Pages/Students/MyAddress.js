import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';
import DataTable from 'react-data-table-component';

export default function MyAddress(props) {
    const { myAddress } = usePage().props;

    const ExpandedComponent = ({ data }) => <pre>{JSON.stringify(data, null, 2)}</pre>;

    const columns = [
        { name: 'Address Name', selector: row => row.address_name, },
        { name: 'Address', selector: row => row.address, },
        { name: 'City', selector: row => row.city, },
        { name: 'state', selector: row => row.state, },
        { name: 'country', selector: row => row.country, },
        { name: 'zipcode', selector: row => row.zipcode, },
    ];

    const data = [];
    myAddress.map((address, i) => {
        data.push({
            address_name: address.address_name,
            address: address.address,
            city: address.city,
            state: address.state,
            country: address.country,
            zipcode: address.zipcode,
        })
    });

    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="My Address" />
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />

                                <div className="accountDtl">
                                    <div className="accountEdit">
                                        <h3>My Address</h3>
                                        <DataTable
                                            pagination
                                            columns={columns}
                                            data={data}
                                            expandableRows={true}
                                            expandableRowsComponent={ExpandedComponent}
                                            expandableRowExpanded={() => !true}
                                            // subHeader
                                            // subHeaderComponent={subHeaderComponentMemo}
                                        />
                                    </div>
                                    {/* <div className="accountActn">
                                        <button className="btn btn-secondary">Cancel</button>
                                        <button className="btn btn-primary">Save</button>
                                    </div> */}
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
