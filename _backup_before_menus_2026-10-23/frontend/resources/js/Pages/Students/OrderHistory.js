import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';
import DataTable from 'react-data-table-component';

export default function OrderHistory(props) {
    const { orderHistory } = usePage().props;

    const ExpandedComponent = ({ data }) => <pre>{JSON.stringify(data, null, 2)}</pre>;

    const columns = [
        { name: 'Order ID', selector: row => row.order_number,},
        { name: 'Course title', selector: row => row.title,},
        { name: 'Amount', selector: row => row.price,},
        { name: 'Currency', selector: row => row.price_type,},
        { name: 'Order Date', selector: row => row.order_date,},
        { name: 'Payment Method', selector: row => row.payment_option,},
    ];
    
    const data = [];
    orderHistory.map((order, i) => {
        data.push({ 
            order_number: order.order_number, 
            title: order.title, 
            price: order.price,
            price_type: order.price_type,
            order_date: order.order_date ,
            payment_option: order.payment_option
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
                <Head title="Order History" />
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />

                                <div className="accountDtl">
                                    <div className="accountEdit">
                                        <h3>Order History</h3>
                                        <DataTable 
                                            pagination
                                            columns={columns}
                                            data={data}
                                            expandableRows={true}
                                            expandableRowsComponent={ExpandedComponent}
                                            expandableRowExpanded={() => !true}
                                        />
                                        {/* {orderHistory.length !== 0 && (
                                            <table>
                                                <th>
                                                    <td>Order Date</td>
                                                    <td>Order ID</td>
                                                    <td>Course Title</td>
                                                </th>
                                                {orderHistory.map((orders, i) => {
                                                    return (
                                                        <tr>
                                                            <td>{orders.created_at}</td>
                                                            <td>{orders.order_id}</td>
                                                            <td>{orders.title}</td>
                                                        </tr>
                                                    );
                                                })}
                                            </table>
                                        )} */}
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
