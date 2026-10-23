import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';
import DataTable from 'react-data-table-component';

export default function OrderHistory(props) {
    const { orderHistory, originals = [], commissions = [] } = usePage().props;
    const money = (a, c) => (a == null ? '' : `${c === 'USD' ? '$' : '₹'}${Number(a).toLocaleString(c === 'USD' ? 'en-US' : 'en-IN')}`);
    const nice = (v) => String(v || '').replace(/_/g, ' ').toLowerCase().replace(/^./, (x) => x.toUpperCase());
    const day = (v) => (v ? String(v).slice(0, 10) : '');

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
                                        {(orderHistory.length > 0 || (originals.length === 0 && commissions.length === 0)) && <h4 className="oh-sub">Courses &amp; workshops</h4>}
                                        <DataTable 
                                            pagination
                                            columns={columns}
                                            data={data}
                                            expandableRows={true}
                                            expandableRowsComponent={ExpandedComponent}
                                            expandableRowExpanded={() => !true}
                                        />
                                        {originals.length > 0 && (
                                            <>
                                                <h4 className="oh-sub">Original artworks</h4>
                                                <div className="oh-table" role="region" aria-label="Original artwork orders" tabIndex={0}>
                                                    <table>
                                                        <thead><tr><th>Order ID</th><th>Artwork</th><th>Amount</th><th>Date</th><th>Status</th></tr></thead>
                                                        <tbody>
                                                            {originals.map((o) => (
                                                                <tr key={o.order_id}>
                                                                    <td><a href={`/marketplace/orders/${o.order_id}`}>{o.order_id}</a></td>
                                                                    <td><a href={`/marketplace/${o.slug}`}>{o.title}</a>{o.artist_name ? <span className="dm-muted"> · {o.artist_name}</span> : null}</td>
                                                                    <td>{money(o.amount, o.currency)}</td>
                                                                    <td>{day(o.created_at)}</td>
                                                                    <td>{nice(o.status)}{o.tracking_number ? <span className="dm-muted"> · {o.courier} {o.tracking_number}</span> : null}</td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            </>
                                        )}
                                        {commissions.length > 0 && (
                                            <>
                                                <h4 className="oh-sub">Commissioned art</h4>
                                                <div className="oh-table" role="region" aria-label="Commission orders" tabIndex={0}>
                                                    <table>
                                                        <thead><tr><th>Order ID</th><th>Type</th><th>Amount</th><th>Date</th><th>Status</th></tr></thead>
                                                        <tbody>
                                                            {commissions.map((o) => (
                                                                <tr key={o.order_id}>
                                                                    <td>{o.order_id}</td>
                                                                    <td>{nice(o.fulfillment_type)}</td>
                                                                    <td>{money(o.total_price, o.currency)}</td>
                                                                    <td>{day(o.created_at)}</td>
                                                                    <td>{nice(o.order_status)}</td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            </>
                                        )}
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
